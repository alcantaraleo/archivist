package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Bm25IndexCacheTest {

    @Test
    void stableCorpusReusesCache() {
        StubCorpus corpus = new StubCorpus(Instant.parse("2026-01-01T00:00:00Z"));
        Bm25IndexCache cache = new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties());

        cache.acquire(corpus);
        cache.acquire(corpus);

        assertEquals(1, cache.rebuildCountForTests());
    }

    @Test
    void fingerprintChangeTriggersRebuild() {
        StubCorpus corpus = new StubCorpus(Instant.parse("2026-01-01T00:00:00Z"));
        Bm25IndexCache cache = new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties());

        cache.acquire(corpus);
        corpus.setUpdated(Instant.parse("2026-01-02T00:00:00Z"));
        cache.acquire(corpus);

        assertEquals(2, cache.rebuildCountForTests());
    }

    @Test
    void ttlExpiryTriggersRebuild() {
        StubCorpus corpus = new StubCorpus(Instant.parse("2026-01-01T00:00:00Z"));
        Bm25Properties properties = new Bm25Properties();
        properties.setIndexTtl(Duration.ofMinutes(5));

        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        Bm25IndexCache cache =
                new Bm25IndexCache(new Bm25IndexBuilder(), properties, new CorpusFingerprint(), clock);

        cache.acquire(corpus);
        clock.advance(Duration.ofMinutes(6));
        cache.acquire(corpus);

        assertEquals(2, cache.rebuildCountForTests());
    }

    @Test
    void failedRebuildDoesNotLeaveClosedIndexReusable() {
        AtomicInteger loadAllCalls = new AtomicInteger();
        StubCorpus corpus = new StubCorpus(Instant.parse("2026-01-01T00:00:00Z")) {
            @Override
            public List<Evidence> loadAll() {
                if (loadAllCalls.incrementAndGet() == 2) {
                    throw new IllegalStateException("forced rebuild failure");
                }
                return super.loadAll();
            }
        };
        Bm25IndexCache cache = new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties());

        cache.acquire(corpus);
        corpus.setUpdated(Instant.parse("2026-01-02T00:00:00Z"));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> cache.acquire(corpus));
        assertEquals("forced rebuild failure", failure.getMessage());

        assertDoesNotThrow(() -> cache.acquire(corpus));
        assertEquals(2, cache.rebuildCountForTests());
        assertEquals(3, loadAllCalls.get());
    }

    private static class StubCorpus implements KnowledgeCorpus {

        private Instant updated;

        private StubCorpus(Instant updated) {
            this.updated = updated;
        }

        void setUpdated(Instant updated) {
            this.updated = updated;
        }

        @Override
        public List<Provenance> catalog() {
            return List.of(provenance());
        }

        @Override
        public java.util.Optional<Evidence> loadBySourceId(String sourceId) {
            return java.util.Optional.of(new Evidence("body", provenance()));
        }

        @Override
        public List<Evidence> loadAll() {
            return List.of(new Evidence("body", provenance()));
        }

        private Provenance provenance() {
            return new Provenance(
                    "wiki/a",
                    "title",
                    KnowledgeType.CONCEPT,
                    KnowledgeZone.SYNTHESIZED,
                    List.of("tag"),
                    List.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    updated,
                    ContentAvailability.AVAILABLE);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone = ZoneId.of("UTC");

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
