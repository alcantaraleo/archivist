package io.archivist.transport.mcp;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.time.Instant;
import java.util.List;

record ProvenanceResponse(
        String sourceId,
        String title,
        KnowledgeType type,
        KnowledgeZone zone,
        List<String> tags,
        List<String> sources,
        Instant created,
        Instant updated) {
}
