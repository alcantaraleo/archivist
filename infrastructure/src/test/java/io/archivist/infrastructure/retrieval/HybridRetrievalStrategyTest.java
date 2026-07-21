package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrievalStrategyTest {

    @Test
    void blankQueryReturnsEmptyWithoutCallingLegs() {
        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy bm25 = named("bm25");
        RetrievalStrategy embedding = named("embedding");
        HybridRetrievalStrategy strategy =
                new HybridRetrievalStrategy(lexical, bm25, embedding, new HybridProperties());

        assertTrue(strategy.retrieve(Query.unrestricted("   ", 5)).isEmpty());
        verify(lexical, never()).retrieve(any());
        verify(bm25, never()).retrieve(any());
        verify(embedding, never()).retrieve(any());
    }

    @Test
    void capsAtMinOfQueryAndHybridMaxResults() {
        Evidence a = evidence("A", "alpha");
        Evidence b = evidence("B", "beta");
        Evidence c = evidence("C", "gamma");

        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy bm25 = named("bm25");
        RetrievalStrategy embedding = named("embedding");
        when(lexical.retrieve(any())).thenReturn(List.of(a, b, c));
        when(bm25.retrieve(any())).thenReturn(List.of(a, b));
        when(embedding.retrieve(any())).thenReturn(List.of(a));

        HybridProperties hybrid = new HybridProperties();
        hybrid.setMaxResults(2);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(lexical, bm25, embedding, hybrid);

        List<Evidence> results = strategy.retrieve(Query.unrestricted("token", 10));

        assertEquals(2, results.size());
        assertEquals("A", results.get(0).provenance().sourceId());
        assertEquals("B", results.get(1).provenance().sourceId());
    }

    @Test
    void prefersEmbeddingEvidenceWhenMultipleLegsReturnSameId() {
        Evidence fromLexical = evidence("A", "lexical-body");
        Evidence fromEmbedding = evidence("A", "embedding-body");

        RetrievalStrategy lexical = named("lexical");
        RetrievalStrategy bm25 = named("bm25");
        RetrievalStrategy embedding = named("embedding");
        when(lexical.retrieve(any())).thenReturn(List.of(fromLexical));
        when(bm25.retrieve(any())).thenReturn(List.of());
        when(embedding.retrieve(any())).thenReturn(List.of(fromEmbedding));

        HybridRetrievalStrategy strategy =
                new HybridRetrievalStrategy(lexical, bm25, embedding, new HybridProperties());

        List<Evidence> results = strategy.retrieve(Query.unrestricted("token", 10));

        assertEquals(1, results.size());
        assertEquals("embedding-body", results.getFirst().content());
    }

    @Test
    void nameIsHybrid() {
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                named("lexical"), named("bm25"), named("embedding"), new HybridProperties());
        assertEquals("hybrid", strategy.name());
    }

    private static RetrievalStrategy named(String name) {
        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        when(strategy.name()).thenReturn(name);
        return strategy;
    }

    private static Evidence evidence(String sourceId, String content) {
        return new Evidence(
                content,
                new Provenance(
                        sourceId,
                        sourceId,
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.AVAILABLE));
    }
}
