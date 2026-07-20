package io.archivist.infrastructure.retrieval;

import io.archivist.domain.port.out.KnowledgeCorpus;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

final class Bm25IndexCache {

    private final Bm25IndexBuilder indexBuilder;
    private final Bm25Properties properties;
    private final CorpusFingerprint corpusFingerprint;
    private final Clock clock;

    private ActiveIndex activeIndex;
    private int rebuildCount;

    Bm25IndexCache(Bm25IndexBuilder indexBuilder, Bm25Properties properties) {
        this(indexBuilder, properties, new CorpusFingerprint(), Clock.systemUTC());
    }

    Bm25IndexCache(
            Bm25IndexBuilder indexBuilder,
            Bm25Properties properties,
            CorpusFingerprint corpusFingerprint,
            Clock clock) {
        this.indexBuilder = Objects.requireNonNull(indexBuilder, "indexBuilder");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.corpusFingerprint = Objects.requireNonNull(corpusFingerprint, "corpusFingerprint");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Rebuild + search under one monitor (research Decision 8) so rebuild cannot close the reader
     * under an in-flight search.
     */
    synchronized <T> T withIndex(KnowledgeCorpus corpus, Function<CachedIndex, T> action) {
        Objects.requireNonNull(action, "action");
        return action.apply(acquire(corpus));
    }

    synchronized CachedIndex acquire(KnowledgeCorpus corpus) {
        Objects.requireNonNull(corpus, "corpus");
        String fingerprint = corpusFingerprint.compute(corpus);
        Instant now = clock.instant();
        if (activeIndex == null || shouldRebuild(fingerprint, now)) {
            rebuild(corpus, fingerprint, now);
        }
        return new CachedIndex(activeIndex.builtIndex(), fingerprint, activeIndex.builtAt());
    }

    synchronized void invalidate() {
        closeActive();
    }

    int rebuildCountForTests() {
        return rebuildCount;
    }

    private boolean shouldRebuild(String fingerprint, Instant now) {
        if (activeIndex == null) {
            return true;
        }
        if (!activeIndex.fingerprint().equals(fingerprint)) {
            return true;
        }
        Instant expiresAt = activeIndex.builtAt().plus(properties.getIndexTtl());
        return now.isAfter(expiresAt);
    }

    private void rebuild(KnowledgeCorpus corpus, String fingerprint, Instant builtAt) {
        closeActive();
        try {
            Bm25IndexBuilder.BuiltIndex builtIndex = indexBuilder.build(corpus, properties);
            rebuildCount++;
            activeIndex = new ActiveIndex(builtIndex, fingerprint, builtAt);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build BM25 index", exception);
        }
    }

    private void closeActive() {
        if (activeIndex == null) {
            return;
        }
        ActiveIndex closing = activeIndex;
        activeIndex = null;
        try {
            closing.builtIndex().close();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close BM25 index", exception);
        }
    }

    record CachedIndex(Bm25IndexBuilder.BuiltIndex builtIndex, String fingerprint, Instant builtAt) {}

    private record ActiveIndex(Bm25IndexBuilder.BuiltIndex builtIndex, String fingerprint, Instant builtAt) {}
}
