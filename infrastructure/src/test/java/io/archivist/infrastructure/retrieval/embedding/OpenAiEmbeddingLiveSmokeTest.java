package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Manual / optional smoke against api.openai.com. Not CI-gated — requires {@code OPENAI_API_KEY}.
 */
@SpringBootTest(classes = RetrievalTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiEmbeddingLiveSmokeTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeGateway knowledgeGateway;

    @BeforeAll
    static void copyFixtureCorpus() throws Exception {
        assumeTrue(System.getenv("OPENAI_API_KEY") != null && !System.getenv("OPENAI_API_KEY").isBlank());
        copyResourceTree("fixture-corpus", corpusRoot);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "embedding");
        registry.add("archivist.retrieval.embedding.embedder", () -> "openai-compatible");
        registry.add("archivist.retrieval.embedding.store", () -> "memory");
        registry.add("archivist.retrieval.embedding.dimensions", () -> "1536");
        registry.add("archivist.retrieval.embedding.openai.base-url", () -> "https://api.openai.com");
        registry.add("archivist.retrieval.embedding.openai.api-key", () -> System.getenv("OPENAI_API_KEY"));
        registry.add("archivist.retrieval.embedding.openai.model", () -> "text-embedding-3-small");
        registry.add("archivist.retrieval.max-results", () -> "10");
    }

    @Test
    @Timeout(120)
    void retrieveContextReturnsEvidenceWithProvenanceViaOpenAiEmbeddings() {
        List<Evidence> results = knowledgeGateway.retrieve(Query.unrestricted("sample concept", 10));
        assertFalse(results.isEmpty());
        Provenance provenance = results.getFirst().provenance();
        assertNotNull(provenance);
        assertFalse(provenance.sourceId().isBlank());
    }

    private static void copyResourceTree(String resourceRoot, Path targetRoot) throws Exception {
        URL resourceUrl = OpenAiEmbeddingLiveSmokeTest.class.getClassLoader().getResource(resourceRoot);
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
}
