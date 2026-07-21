package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = RetrievalTestConfiguration.class)
class RetrievalStrategyRegistryTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private List<RetrievalStrategy> strategies;

    @Autowired
    private RetrievalStrategyRegistry registry;

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        try {
            Files.createDirectories(corpusRoot);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "bm25");
    }

    @Test
    void springRegistersRealBm25StrategyBean() {
        assertTrue(strategies.stream().anyMatch(strategy -> "lexical".equals(strategy.name())));
        assertTrue(strategies.stream().anyMatch(strategy -> "bm25".equals(strategy.name())));
        assertTrue(strategies.stream().anyMatch(strategy -> "embedding".equals(strategy.name())));
        assertTrue(strategies.stream().anyMatch(strategy -> "hybrid".equals(strategy.name())));
        assertInstanceOf(Bm25RetrievalStrategy.class, registry.getActive());
        assertEquals("bm25", registry.getActive().name());
    }

    @Test
    void resolvesHybridWhenRegistered() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy bm25 = named("bm25");
        RetrievalStrategy embedding = named("embedding");
        RetrievalStrategy hybrid = named("hybrid");
        RetrievalStrategyRegistry unitRegistry =
                new RetrievalStrategyRegistry(List.of(lexical, bm25, embedding, hybrid), "hybrid");

        assertSame(hybrid, unitRegistry.getActive());
    }

    @Test
    void resolvesEmbeddingWhenRegistered() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy embedding = named("embedding");
        RetrievalStrategyRegistry unitRegistry =
                new RetrievalStrategyRegistry(List.of(lexical, embedding), "embedding");

        assertSame(embedding, unitRegistry.getActive());
    }

    @Test
    void resolvesLexicalWhenRegistered() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategyRegistry unitRegistry = new RetrievalStrategyRegistry(List.of(lexical), "lexical");

        assertSame(lexical, unitRegistry.getActive());
    }

    @Test
    void resolvesBm25WhenRegistered() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy bm25 = named("bm25");
        RetrievalStrategyRegistry unitRegistry =
                new RetrievalStrategyRegistry(List.of(lexical, bm25), "bm25");

        assertSame(bm25, unitRegistry.getActive());
    }

    @Test
    void unknownActiveStrategyFailsConstruction() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new RetrievalStrategyRegistry(List.of(named("lexical")), "bm25"));

        assertTrue(exception.getMessage().contains("bm25"));
        assertTrue(exception.getMessage().contains("lexical"));
    }

    @Test
    void duplicateNamesFailConstruction() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new RetrievalStrategyRegistry(List.of(named("lexical"), named("lexical")), "lexical"));

        assertTrue(exception.getMessage().contains("Duplicate"));
        assertEquals("lexical", named("lexical").name());
    }

    private static RetrievalStrategy named(String name) {
        return new RetrievalStrategy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Evidence> retrieve(Query query) {
                return List.of();
            }
        };
    }
}
