package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalStrategyRegistryTest {

    @Test
    void resolvesLexicalWhenRegistered() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategyRegistry registry = new RetrievalStrategyRegistry(List.of(lexical), "lexical");

        assertSame(lexical, registry.getActive());
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
