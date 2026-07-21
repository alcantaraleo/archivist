package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextEmbedderRegistryTest {

    @Test
    void unknownIdListsRegisteredNames() {
        TextEmbedderRegistry registry = new TextEmbedderRegistry(List.of(new StubTextEmbedder(8)));
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> registry.require("missing"));
        assertTrue(exception.getMessage().contains("stub"));
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void duplicateRegistrationFails() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TextEmbedderRegistry(List.of(new StubTextEmbedder(8), new StubTextEmbedder(8))));
        assertTrue(exception.getMessage().contains("Duplicate"));
        assertEquals("stub", new StubTextEmbedder(8).name());
    }
}
