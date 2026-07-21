package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.retrieval.CorpusFingerprint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingIndexCacheTest {

    @Test
    void rebuildsWhenFingerprintChanges() {
        KnowledgeCorpus corpus = mock(KnowledgeCorpus.class);
        Evidence evidence = evidence("a", "alpha token");
        when(corpus.catalog()).thenReturn(List.of(evidence.provenance()));
        when(corpus.loadAll()).thenReturn(List.of(evidence));

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(16);
        properties.setEmbedder("stub");
        properties.setIndexTtl(Duration.ofMinutes(15));

        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        EmbeddingIndexCache cache = new EmbeddingIndexCache(
                new StubTextEmbedder(16),
                new InMemoryVectorStore(),
                new EntryChunker(properties),
                properties,
                new CorpusFingerprint(),
                clock);

        cache.ensureIndex(corpus);
        assertEquals(1, cache.rebuildCountForTests());

        cache.ensureIndex(corpus);
        assertEquals(1, cache.rebuildCountForTests());

        Evidence changed = evidence("a", "alpha token");
        Provenance updated = new Provenance(
                changed.provenance().sourceId(),
                changed.provenance().title(),
                changed.provenance().type(),
                changed.provenance().zone(),
                changed.provenance().tags(),
                changed.provenance().sources(),
                changed.provenance().created(),
                Instant.parse("2026-06-01T00:00:00Z"),
                changed.provenance().contentAvailability());
        when(corpus.catalog()).thenReturn(List.of(updated));
        when(corpus.loadAll()).thenReturn(List.of(new Evidence(changed.content(), updated)));

        cache.ensureIndex(corpus);
        assertEquals(2, cache.rebuildCountForTests());
    }

    @Test
    void rebuildsWhenTtlExpires() {
        KnowledgeCorpus corpus = mock(KnowledgeCorpus.class);
        Evidence evidence = evidence("a", "alpha token");
        when(corpus.catalog()).thenReturn(List.of(evidence.provenance()));
        when(corpus.loadAll()).thenReturn(List.of(evidence));

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(16);
        properties.setIndexTtl(Duration.ofMinutes(1));

        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        EmbeddingIndexCache cache = new EmbeddingIndexCache(
                new StubTextEmbedder(16),
                new InMemoryVectorStore(),
                new EntryChunker(properties),
                properties,
                new CorpusFingerprint(),
                clock);

        cache.ensureIndex(corpus);
        assertEquals(1, cache.rebuildCountForTests());

        clock.advance(Duration.ofMinutes(2));
        cache.ensureIndex(corpus);
        assertEquals(2, cache.rebuildCountForTests());
    }

    @Test
    void indexRebuildEmbedsInFixedBatchSizes() {
        KnowledgeCorpus corpus = mock(KnowledgeCorpus.class);
        List<Evidence> entries = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            entries.add(evidence("id-" + i, "token-" + i));
        }
        when(corpus.catalog()).thenReturn(entries.stream().map(Evidence::provenance).toList());
        when(corpus.loadAll()).thenReturn(entries);

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(8);
        properties.setChunkSizeChars(100);
        properties.setChunkOverlapChars(0);

        BatchRecordingEmbedder embedder = new BatchRecordingEmbedder(8);
        EmbeddingIndexCache cache = new EmbeddingIndexCache(
                embedder,
                new InMemoryVectorStore(),
                new EntryChunker(properties),
                properties);

        cache.ensureIndex(corpus);
        assertEquals(List.of(32, 8), embedder.batchSizes());
    }

    private static Evidence evidence(String sourceId, String body) {
        Provenance provenance = new Provenance(
                sourceId,
                "Title " + sourceId,
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                List.of("tag"),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                ContentAvailability.AVAILABLE);
        return new Evidence(body, provenance);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class BatchRecordingEmbedder implements TextEmbedder {

        private final int dimensions;
        private final List<Integer> batchSizes = new ArrayList<>();

        private BatchRecordingEmbedder(int dimensions) {
            this.dimensions = dimensions;
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public float[] embed(String text) {
            return embedBatch(List.of(text)).getFirst();
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            batchSizes.add(texts.size());
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new float[dimensions]);
            }
            return vectors;
        }
    }
}
