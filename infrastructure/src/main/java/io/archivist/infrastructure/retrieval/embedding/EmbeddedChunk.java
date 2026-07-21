package io.archivist.infrastructure.retrieval.embedding;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;

public record EmbeddedChunk(
        String sourceId,
        int chunkIndex,
        String text,
        float[] vector,
        KnowledgeType knowledgeType,
        KnowledgeZone knowledgeZone) {
}
