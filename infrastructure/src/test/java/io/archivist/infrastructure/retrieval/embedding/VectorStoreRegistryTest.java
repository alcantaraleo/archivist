package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VectorStoreRegistryTest {

    @Test
    void unknownIdListsRegisteredNames() {
        VectorStoreRegistry registry = new VectorStoreRegistry(List.of(new InMemoryVectorStore()));
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> registry.require("pgvector"));
        assertTrue(exception.getMessage().contains("memory"));
        assertTrue(exception.getMessage().contains("pgvector"));
    }

    @Test
    void duplicateRegistrationFails() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new VectorStoreRegistry(List.of(new InMemoryVectorStore(), new InMemoryVectorStore())));
        assertTrue(exception.getMessage().contains("Duplicate"));
    }
}
