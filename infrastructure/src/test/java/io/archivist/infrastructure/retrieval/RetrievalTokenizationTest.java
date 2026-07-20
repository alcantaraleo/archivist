package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RetrievalTokenizationTest {

    @Test
    void splitsPunctuationAndLowercases() {
        assertEquals(
                java.util.List.of("hello", "world", "topic"),
                RetrievalTokenization.tokenize("Hello, WORLD! topic"));
    }

    @Test
    void dropsEmptySegments() {
        assertEquals(java.util.List.of("a", "b"), RetrievalTokenization.tokenize("  a!!!   b  "));
    }
}
