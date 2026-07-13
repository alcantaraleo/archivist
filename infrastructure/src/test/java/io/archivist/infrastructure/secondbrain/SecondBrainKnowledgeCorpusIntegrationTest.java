package io.archivist.infrastructure.secondbrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.secondbrain.support.SecondBrainCorpusTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)
class SecondBrainKnowledgeCorpusIntegrationTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeCorpus knowledgeCorpus;

    @BeforeAll
    static void copyFixtureCorpusAndAddOversizeEntry() throws Exception {
        copyResourceTree("fixture-corpus", corpusRoot);
        writeOversizeEntry(corpusRoot);
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
    }

    @Test
    void shouldMatchFixtureCatalogContract() throws Exception {
        JsonNode expected = readFixtureContract();
        List<Provenance> catalog = knowledgeCorpus.catalog();
        Map<String, Provenance> catalogBySourceId =
                catalog.stream().collect(Collectors.toMap(Provenance::sourceId, Function.identity()));

        long fixtureEntryCount = catalog.stream()
                .filter(entry -> !entry.sourceId().equals("wiki/concepts/oversize-entry"))
                .count();
        assertEquals(expected.get("catalogSize").intValue(), fixtureEntryCount);

        for (JsonNode entry : expected.get("entries")) {
            String sourceId = entry.get("sourceId").stringValue();
            Provenance provenance = catalogBySourceId.get(sourceId);
            assertEquals(entry.get("title").stringValue(), provenance.title());
            assertEquals(KnowledgeType.valueOf(entry.get("type").stringValue()), provenance.type());
            assertEquals(KnowledgeZone.valueOf(entry.get("zone").stringValue()), provenance.zone());
            assertEquals(toStringList(entry.get("tags")), provenance.tags());
            assertEquals(toStringList(entry.get("sources")), provenance.sources());
            assertEquals(ContentAvailability.AVAILABLE, provenance.contentAvailability());
        }

        for (JsonNode excluded : expected.get("excludedSourceIds")) {
            assertFalse(catalogBySourceId.containsKey(excluded.stringValue()));
        }
    }

    @Test
    void shouldLoadAllBodiesMatchingFixtureContract() throws Exception {
        JsonNode expected = readFixtureContract();
        Map<String, Evidence> evidenceBySourceId = knowledgeCorpus.loadAll().stream()
                .collect(Collectors.toMap(evidence -> evidence.provenance().sourceId(), Function.identity()));

        for (JsonNode entry : expected.get("entries")) {
            Evidence evidence = evidenceBySourceId.get(entry.get("sourceId").stringValue());
            assertEquals(entry.get("bodyEquals").stringValue(), evidence.content());
        }
    }

    @Test
    void shouldResolveSourcesChainOnSampleWithSources() {
        Optional<Evidence> evidence = knowledgeCorpus.loadBySourceId("wiki/concepts/sample-with-sources");

        assertTrue(evidence.isPresent());
        assertEquals(List.of("raw/readings/sample-reading"), evidence.get().provenance().sources());
    }

    @Test
    void shouldResolveLoadBySourceIdHitAndMiss() throws Exception {
        JsonNode expected = readFixtureContract();
        String hitId = expected.get("loadBySourceIdCases").get("hit").stringValue();
        String missId = expected.get("loadBySourceIdCases").get("miss").stringValue();

        assertTrue(knowledgeCorpus.loadBySourceId(hitId).isPresent());
        assertTrue(knowledgeCorpus.loadBySourceId(missId).isEmpty());
        assertTrue(knowledgeCorpus.loadBySourceId("").isEmpty());
    }

    @Test
    void shouldFlagOversizeEntryWithoutLoadingBody() {
        List<Provenance> catalog = knowledgeCorpus.catalog();
        Optional<Provenance> oversize = catalog.stream()
                .filter(entry -> entry.sourceId().equals("wiki/concepts/oversize-entry"))
                .findFirst();

        assertTrue(oversize.isPresent());
        assertEquals(ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE, oversize.get().contentAvailability());
        assertEquals(KnowledgeType.CONCEPT, oversize.get().type());
        assertEquals("Oversize Entry", oversize.get().title());

        Optional<Evidence> evidence = knowledgeCorpus.loadBySourceId("wiki/concepts/oversize-entry");
        assertTrue(evidence.isPresent());
        assertEquals("", evidence.get().content());
        assertEquals(
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE,
                evidence.get().provenance().contentAvailability());
    }

    private static JsonNode readFixtureContract() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        Path fixturePath =
                Path.of("specs/003-second-brain-integration/contracts/fixture-catalog-expected.json");
        return jsonMapper.readTree(fixturePath.toFile());
    }

    private static List<String> toStringList(JsonNode node) {
        return node.valueStream().map(JsonNode::stringValue).toList();
    }

    private static void copyResourceTree(String resourceRoot, Path targetRoot) throws Exception {
        URL resourceUrl =
                SecondBrainKnowledgeCorpusIntegrationTest.class.getClassLoader().getResource(resourceRoot);
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
        Path oversizePath = root.resolve("wiki/concepts/oversize-entry.md");
        Files.createDirectories(oversizePath.getParent());
        String frontmatter =
                """
                ---
                title: Oversize Entry
                type: concept
                created: 2026-01-01T00:00:00Z
                updated: 2026-01-02T00:00:00Z
                ---
                """;
        char[] padding = new char[1_048_576];
        Arrays.fill(padding, 'x');
        Files.writeString(oversizePath, frontmatter + new String(padding));
    }
}
