package io.archivist.infrastructure.retrieval.embedding;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.retrieval.CorpusFingerprint;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class EmbeddingIndexCache {

    // ponytail: fixed batch size avoids single huge embedBatch for remote APIs; raise when adapters support it
    static final int INDEX_EMBED_BATCH_SIZE = 32;

    private final TextEmbedder embedder;
    private final VectorStore vectorStore;
    private final EntryChunker chunker;
    private final EmbeddingProperties properties;
    private final CorpusFingerprint corpusFingerprint;
    private final Clock clock;
    private final String embedderIdentity;

    private ActiveIndex activeIndex;
    private int rebuildCount;

    public EmbeddingIndexCache(
            TextEmbedder embedder,
            VectorStore vectorStore,
            EntryChunker chunker,
            EmbeddingProperties properties) {
        this(embedder, vectorStore, chunker, properties, new CorpusFingerprint(), Clock.systemUTC());
    }

    public EmbeddingIndexCache(
            TextEmbedder embedder,
            VectorStore vectorStore,
            EntryChunker chunker,
            EmbeddingProperties properties,
            CorpusFingerprint corpusFingerprint,
            Clock clock) {
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.corpusFingerprint = Objects.requireNonNull(corpusFingerprint, "corpusFingerprint");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.embedderIdentity = embedder.name() + "|" + embedder.dimensions() + "|"
                + properties.getLocal().getModelResource() + "|"
                + properties.getOpenai().getModel() + "|"
                + properties.getOpenai().getBaseUrl();
        if (embedder.dimensions() != properties.getDimensions()) {
            throw new IllegalStateException(
                    "Embedder dimensions "
                            + embedder.dimensions()
                            + " do not match archivist.retrieval.embedding.dimensions "
                            + properties.getDimensions());
        }
    }

    synchronized <T> T withStore(KnowledgeCorpus corpus, Function<VectorStore, T> action) {
        Objects.requireNonNull(action, "action");
        ensureIndex(corpus);
        return action.apply(vectorStore);
    }

    synchronized void ensureIndex(KnowledgeCorpus corpus) {
        Objects.requireNonNull(corpus, "corpus");
        String fingerprint = corpusFingerprint.compute(corpus);
        Instant now = clock.instant();
        if (activeIndex == null || shouldRebuild(fingerprint, now)) {
            rebuild(corpus, fingerprint, now);
        }
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
        if (!activeIndex.embedderIdentity().equals(embedderIdentity)) {
            return true;
        }
        Instant expiresAt = activeIndex.builtAt().plus(properties.getIndexTtl());
        return now.isAfter(expiresAt);
    }

    private void rebuild(KnowledgeCorpus corpus, String fingerprint, Instant builtAt) {
        List<Evidence> entries = corpus.loadAll();
        List<EmbeddedChunk> chunks = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<ChunkMeta> metas = new ArrayList<>();
        for (Evidence evidence : entries) {
            Provenance provenance = evidence.provenance();
            for (EntryChunker.ChunkText chunkText : chunker.chunk(evidence)) {
                texts.add(chunkText.text());
                metas.add(new ChunkMeta(
                        provenance.sourceId(),
                        chunkText.chunkIndex(),
                        chunkText.text(),
                        provenance.type(),
                        provenance.zone()));
            }
        }
        List<float[]> vectors = texts.isEmpty() ? List.of() : embedInBatches(texts);
        if (vectors.size() != metas.size()) {
            throw new IllegalStateException(
                    "embedBatch size " + vectors.size() + " != chunk count " + metas.size());
        }
        for (int i = 0; i < metas.size(); i++) {
            ChunkMeta meta = metas.get(i);
            chunks.add(new EmbeddedChunk(
                    meta.sourceId(),
                    meta.chunkIndex(),
                    meta.text(),
                    vectors.get(i),
                    meta.knowledgeType(),
                    meta.knowledgeZone()));
        }
        vectorStore.replaceAll(chunks);
        rebuildCount++;
        activeIndex = new ActiveIndex(fingerprint, embedderIdentity, builtAt);
    }

    private List<float[]> embedInBatches(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += INDEX_EMBED_BATCH_SIZE) {
            int to = Math.min(from + INDEX_EMBED_BATCH_SIZE, texts.size());
            vectors.addAll(embedder.embedBatch(texts.subList(from, to)));
        }
        return vectors;
    }

    private record ChunkMeta(
            String sourceId,
            int chunkIndex,
            String text,
            io.archivist.domain.model.KnowledgeType knowledgeType,
            io.archivist.domain.model.KnowledgeZone knowledgeZone) {}

    private record ActiveIndex(String fingerprint, String embedderIdentity, Instant builtAt) {}
}
