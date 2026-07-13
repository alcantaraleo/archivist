package io.archivist.infrastructure.secondbrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorpusEntryMapperTest {

    private final CorpusEntryMapper mapper =
            new CorpusEntryMapper(new SourceIdNormalizer(), new ZoneTypeResolver(defaultZonePrefixes(), Map.of()));

    @Test
    void shouldMapAvailableEntryWithBodyAndFlag() {
        Path corpusRoot = Path.of("/corpus");
        Path filePath = Path.of("/corpus/wiki/concepts/sample-concept.md");
        Map<String, Object> metadata = Map.of(
                "title", "Sample Concept",
                "type", "concept",
                "tags", List.of("architecture", "fixture"));
        Instant modified = Instant.parse("2026-01-02T00:00:00Z");

        var mapped = mapper.map(
                corpusRoot,
                filePath,
                metadata,
                "Concept body explaining the sample.",
                ContentAvailability.AVAILABLE,
                modified);

        assertTrue(mapped.isPresent());
        assertEquals("Concept body explaining the sample.", mapped.get().content());
        assertEquals(ContentAvailability.AVAILABLE, mapped.get().provenance().contentAvailability());
        assertEquals(KnowledgeType.CONCEPT, mapped.get().provenance().type());
        assertEquals(KnowledgeZone.SYNTHESIZED, mapped.get().provenance().zone());
    }

    @Test
    void shouldMapOversizeEntryWithEmptyContentAndFlag() {
        Path corpusRoot = Path.of("/corpus");
        Path filePath = Path.of("/corpus/wiki/concepts/oversize.md");
        Map<String, Object> metadata = Map.of("title", "Oversize Entry", "type", "concept");
        Instant modified = Instant.parse("2026-01-02T00:00:00Z");

        var mapped = mapper.map(
                corpusRoot, filePath, metadata, "", ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE, modified);

        assertTrue(mapped.isPresent());
        assertEquals("", mapped.get().content());
        assertEquals(
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE,
                mapped.get().provenance().contentAvailability());
        assertEquals("wiki/concepts/oversize", mapped.get().provenance().sourceId());
    }

    private static Map<String, KnowledgeZone> defaultZonePrefixes() {
        Map<String, KnowledgeZone> defaults = new LinkedHashMap<>();
        defaults.put("raw/", KnowledgeZone.SOURCE);
        defaults.put("wiki/", KnowledgeZone.SYNTHESIZED);
        defaults.put("dev/", KnowledgeZone.TECHNICAL);
        return defaults;
    }
}
