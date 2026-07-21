package io.archivist.infrastructure.retrieval.embedding;

public record ScoredChunk(EmbeddedChunk chunk, float score) {
}
