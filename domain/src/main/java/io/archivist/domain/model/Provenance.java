package io.archivist.domain.model;

import java.time.Instant;
import java.util.List;

public record Provenance(
        String sourceId,
        String title,
        KnowledgeType type,
        KnowledgeZone zone,
        List<String> tags,
        List<String> sources,
        Instant created,
        Instant updated) {
}
