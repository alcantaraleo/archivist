package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

    @Test
    void searchOrdersByCosineAndRespectsTopK() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.replaceAll(List.of(
                chunk("a", 0, vec(1, 0, 0)),
                chunk("b", 0, vec(0.7f, 0.7f, 0)),
                chunk("c", 0, vec(0, 1, 0))));

        List<ScoredChunk> hits = store.search(vec(1, 0, 0), 2);
        assertEquals(2, hits.size());
        assertEquals("a", hits.get(0).chunk().sourceId());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
    }

    @Test
    void replaceAllClearsPreviousEntries() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.replaceAll(List.of(chunk("old", 0, vec(1, 0))));
        store.replaceAll(List.of(chunk("new", 0, vec(0, 1))));
        List<ScoredChunk> hits = store.search(vec(0, 1), 5);
        assertEquals(1, hits.size());
        assertEquals("new", hits.getFirst().chunk().sourceId());
    }

    @Test
    void clearEmptiesStore() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.replaceAll(List.of(chunk("x", 0, vec(1, 0))));
        store.clear();
        assertTrue(store.search(vec(1, 0), 5).isEmpty());
    }

    private static EmbeddedChunk chunk(String sourceId, int index, float[] vector) {
        return new EmbeddedChunk(
                sourceId, index, "t", vector, KnowledgeType.CONCEPT, KnowledgeZone.SYNTHESIZED);
    }

    private static float[] vec(float... values) {
        return values;
    }
}
