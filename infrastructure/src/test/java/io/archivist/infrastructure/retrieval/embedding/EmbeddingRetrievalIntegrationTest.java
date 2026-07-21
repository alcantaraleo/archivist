package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = RetrievalTestConfiguration.class)
class EmbeddingRetrievalIntegrationTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeGateway knowledgeGateway;

    @BeforeAll
    static void copyFixtureCorpusAndAddOversizeEntry() throws Exception {
        copyResourceTree("fixture-corpus", corpusRoot);
        writeOversizeEntry(corpusRoot);
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "embedding");
        registry.add("archivist.retrieval.embedding.embedder", () -> "stub");
        registry.add("archivist.retrieval.embedding.store", () -> "memory");
        registry.add("archivist.retrieval.embedding.dimensions", () -> "64");
        registry.add("archivist.retrieval.max-results", () -> "20");
    }

    @Test
    @Timeout(5)
    void retrieveContextReturnsEvidenceWithProvenance() {
        List<Evidence> results = knowledgeGateway.retrieve(Query.unrestricted("sample concept", 20));
        assertFalse(results.isEmpty());
        assertProvenanceComplete(results);
    }

    @Test
    void findDecisionsOnlyReturnsDecisions() {
        List<Evidence> results =
                knowledgeGateway.retrieve(Query.withType("decision", KnowledgeType.DECISION, 20));
        for (Evidence evidence : results) {
            assertEquals(KnowledgeType.DECISION, evidence.provenance().type());
        }
    }

    @Test
    void findConceptsOnlyReturnsConceptOrSynthesis() {
        Set<KnowledgeType> allowed = Set.of(KnowledgeType.CONCEPT, KnowledgeType.SYNTHESIS);
        List<Evidence> results = knowledgeGateway.retrieve(Query.withTypes("concept", allowed, 20));
        for (Evidence evidence : results) {
            assertTrue(allowed.contains(evidence.provenance().type()));
        }
    }

    @Test
    void findPeopleOnlyReturnsPerson() {
        List<Evidence> results =
                knowledgeGateway.retrieve(Query.withType("person", KnowledgeType.PERSON, 20));
        for (Evidence evidence : results) {
            assertEquals(KnowledgeType.PERSON, evidence.provenance().type());
        }
    }

    @Test
    void dedupesBySourceId() {
        List<Evidence> results = knowledgeGateway.retrieve(Query.unrestricted("sample", 50));
        Set<String> seen = new HashSet<>();
        for (Evidence evidence : results) {
            assertTrue(seen.add(evidence.provenance().sourceId()));
        }
    }

    @Test
    void respectsMaxResults() {
        List<Evidence> results = knowledgeGateway.retrieve(Query.unrestricted("sample", 1));
        assertTrue(results.size() <= 1);
    }

    @Test
    void oversizeTitleMatchIncludesEmptyBody() {
        List<Evidence> results =
                knowledgeGateway.retrieve(Query.unrestricted("oversize-fixture-title", 20));
        Evidence match = results.stream()
                .filter(e -> e.provenance().title().contains("oversize-fixture-title"))
                .findFirst()
                .orElseThrow();
        assertEquals("", match.content());
        assertEquals(
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE, match.provenance().contentAvailability());
    }

    @Test
    void multiChunkLongEntryReturnsAtMostOneEvidence() {
        List<Evidence> results =
                knowledgeGateway.retrieve(Query.unrestricted("UNIQUE_CHUNK_TOKEN_OMEGA", 20));
        long count = results.stream()
                .filter(e -> e.provenance().title().contains("Long embedding")
                        || e.content().contains("UNIQUE_CHUNK_TOKEN_OMEGA"))
                .count();
        assertTrue(count <= 1);
    }

    private static void assertProvenanceComplete(List<Evidence> results) {
        for (Evidence evidence : results) {
            Provenance provenance = evidence.provenance();
            assertNotNull(provenance);
            assertFalse(provenance.sourceId().isBlank());
            assertNotNull(provenance.title());
            assertNotNull(provenance.type());
            assertNotNull(provenance.zone());
            assertNotNull(provenance.tags());
            assertNotNull(provenance.sources());
            assertNotNull(provenance.created());
            assertNotNull(provenance.updated());
            assertNotNull(provenance.contentAvailability());
        }
    }

    private static void copyResourceTree(String resourceRoot, Path targetRoot) throws Exception {
        URL resourceUrl = EmbeddingRetrievalIntegrationTest.class.getClassLoader().getResource(resourceRoot);
        Path sourceRoot = Path.of(resourceUrl.toURI());
        try (var paths = Files.walk(sourceRoot)) {
            paths.forEach(sourcePath -> {
                Path destination = targetRoot.resolve(sourceRoot.relativize(sourcePath));
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(sourcePath, destination);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
    }

    private static void writeOversizeEntry(Path root) throws IOException {
        Path oversizePath = root.resolve("edge-cases/oversize-entry.md");
        Files.createDirectories(oversizePath.getParent());
        String frontmatter =
                """
                ---
                title: oversize-fixture-title
                type: concept
                zone: SYNTHESIZED
                created: 2026-01-01T00:00:00Z
                updated: 2026-01-02T00:00:00Z
                ---

                unique-body-only-token-xyz
                """;
        char[] padding = new char[1_048_576];
        Arrays.fill(padding, 'x');
        Files.writeString(oversizePath, frontmatter + new String(padding));
    }
}
