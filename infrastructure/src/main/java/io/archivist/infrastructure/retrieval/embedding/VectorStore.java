package io.archivist.infrastructure.retrieval.embedding;

import java.util.List;

public interface VectorStore {

    String name();

    void replaceAll(List<EmbeddedChunk> chunks);

    List<ScoredChunk> search(float[] queryVector, int topK);

    void clear();
}
