package io.archivist.infrastructure.retrieval.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class InMemoryVectorStore implements VectorStore {

    static final String NAME = "memory";

    private List<EmbeddedChunk> chunks = List.of();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public synchronized void replaceAll(List<EmbeddedChunk> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        List<EmbeddedChunk> normalized = new ArrayList<>(chunks.size());
        for (EmbeddedChunk chunk : chunks) {
            Objects.requireNonNull(chunk, "chunk");
            normalized.add(new EmbeddedChunk(
                    chunk.sourceId(),
                    chunk.chunkIndex(),
                    chunk.text(),
                    VectorMath.l2Normalize(chunk.vector()),
                    chunk.knowledgeType(),
                    chunk.knowledgeZone()));
        }
        this.chunks = List.copyOf(normalized);
    }

    @Override
    public synchronized List<ScoredChunk> search(float[] queryVector, int topK) {
        Objects.requireNonNull(queryVector, "queryVector");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1");
        }
        float[] normalizedQuery = VectorMath.l2Normalize(queryVector);
        List<ScoredChunk> scored = new ArrayList<>(chunks.size());
        for (EmbeddedChunk chunk : chunks) {
            scored.add(new ScoredChunk(chunk, VectorMath.cosineSimilarity(normalizedQuery, chunk.vector())));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.size() <= topK) {
            return List.copyOf(scored);
        }
        return List.copyOf(scored.subList(0, topK));
    }

    @Override
    public synchronized void clear() {
        chunks = List.of();
    }
}
