package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StubTextEmbedderTest {

    @Test
    void sameTextYieldsSameVector() {
        StubTextEmbedder embedder = new StubTextEmbedder(32);
        assertArrayEquals(embedder.embed("hello world"), embedder.embed("hello world"));
    }

    @Test
    void dimensionsMatchConfiguredSize() {
        StubTextEmbedder embedder = new StubTextEmbedder(16);
        assertEquals(16, embedder.embed("x").length);
        assertEquals(16, embedder.dimensions());
    }

    @Test
    void blankTextReturnsZeroVectorNormalizedAsZeros() {
        StubTextEmbedder embedder = new StubTextEmbedder(8);
        float[] vector = embedder.embed("   ");
        assertEquals(8, vector.length);
        for (float value : vector) {
            assertEquals(0.0f, value);
        }
    }
}
