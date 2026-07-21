package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbeddingRetrievalStrategyTest {

    @Test
    void blankQueryReturnsEmpty() {
        EmbeddingRetrievalStrategy strategy = strategy(corpus(List.of(concept("c1", "hello world"))));
        assertTrue(strategy.retrieve(Query.unrestricted("   ", 10)).isEmpty());
    }

    @Test
    void typeFilterExcludesNonMatching() {
        Evidence decision = typed("d1", KnowledgeType.DECISION, "architecture choice");
        Evidence concept = typed("c1", KnowledgeType.CONCEPT, "architecture choice");
        EmbeddingRetrievalStrategy strategy = strategy(corpus(List.of(decision, concept)));

        List<Evidence> results =
                strategy.retrieve(Query.withType("architecture", KnowledgeType.DECISION, 10));
        assertEquals(1, results.size());
        assertEquals("d1", results.getFirst().provenance().sourceId());
    }

    @Test
    void multiChunkDedupesToOneEvidence() {
        String longBody = ("paragraph about omega probe token.\n\n").repeat(40);
        Evidence entry = typed("long1", KnowledgeType.CONCEPT, longBody);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(32);
        properties.setChunkSizeChars(120);
        properties.setChunkOverlapChars(20);
        properties.setTopK(50);
        EmbeddingRetrievalStrategy strategy = strategy(corpus(List.of(entry)), properties);

        List<Evidence> results = strategy.retrieve(Query.unrestricted("omega probe token", 10));
        assertEquals(1, results.size());
        assertEquals("long1", results.getFirst().provenance().sourceId());
    }

    @Test
    void sizeLimitedTitleMatchIncludesEmptyBody() {
        Evidence oversize = new Evidence(
                "",
                new Provenance(
                        "oversize1",
                        "unique-oversize-title-token",
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of("tag"),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE));
        EmbeddingRetrievalStrategy strategy = strategy(corpus(List.of(oversize)));
        List<Evidence> results =
                strategy.retrieve(Query.unrestricted("unique-oversize-title-token", 10));
        assertEquals(1, results.size());
        assertEquals("", results.getFirst().content());
        assertEquals(
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE,
                results.getFirst().provenance().contentAvailability());
    }

    @Test
    void respectsMaxResults() {
        List<Evidence> entries = List.of(
                concept("a", "shared token alpha"),
                concept("b", "shared token beta"),
                concept("c", "shared token gamma"));
        EmbeddingRetrievalStrategy strategy = strategy(corpus(entries));
        List<Evidence> results = strategy.retrieve(Query.unrestricted("shared token", 2));
        assertEquals(2, results.size());
        assertEquals(2, Set.copyOf(results.stream().map(e -> e.provenance().sourceId()).toList()).size());
    }

    private static EmbeddingRetrievalStrategy strategy(KnowledgeCorpus corpus) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(32);
        properties.setTopK(50);
        return strategy(corpus, properties);
    }

    private static EmbeddingRetrievalStrategy strategy(KnowledgeCorpus corpus, EmbeddingProperties properties) {
        TextEmbedder embedder = new StubTextEmbedder(properties.getDimensions());
        VectorStore store = new InMemoryVectorStore();
        EntryChunker chunker = new EntryChunker(properties);
        EmbeddingIndexCache cache = new EmbeddingIndexCache(embedder, store, chunker, properties);
        return new EmbeddingRetrievalStrategy(corpus, embedder, cache, properties);
    }

    private static KnowledgeCorpus corpus(List<Evidence> entries) {
        KnowledgeCorpus corpus = mock(KnowledgeCorpus.class);
        when(corpus.loadAll()).thenReturn(entries);
        when(corpus.catalog()).thenReturn(entries.stream().map(Evidence::provenance).toList());
        when(corpus.loadBySourceId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return entries.stream()
                    .filter(e -> e.provenance().sourceId().equals(id))
                    .findFirst();
        });
        return corpus;
    }

    private static Evidence concept(String sourceId, String body) {
        return typed(sourceId, KnowledgeType.CONCEPT, body);
    }

    private static Evidence typed(String sourceId, KnowledgeType type, String body) {
        return new Evidence(
                body,
                new Provenance(
                        sourceId,
                        "Title " + sourceId,
                        type,
                        KnowledgeZone.SYNTHESIZED,
                        List.of("tag"),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.AVAILABLE));
    }
}
