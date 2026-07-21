package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntryChunkerTest {

    @Test
    void longBodyProducesMultipleChunksWithOverlap() {
        EntryChunker chunker = new EntryChunker(120, 20);
        String body = "A".repeat(200) + "\n\n" + "B".repeat(200);
        List<EntryChunker.ChunkText> chunks = chunker.chunk(evidence("title", List.of("t1"), body, ContentAvailability.AVAILABLE));
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.getFirst().text().startsWith("Title: title"));
    }

    @Test
    void oversizeOmitsBody() {
        EntryChunker chunker = new EntryChunker(1200, 150);
        List<EntryChunker.ChunkText> chunks = chunker.chunk(
                evidence("oversize-title", List.of("tag"), "secret-body", ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE));
        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().text().contains("oversize-title"));
        assertTrue(!chunks.getFirst().text().contains("secret-body"));
    }

    @Test
    void shortEntrySingleChunk() {
        EntryChunker chunker = new EntryChunker(1200, 150);
        List<EntryChunker.ChunkText> chunks =
                chunker.chunk(evidence("short", List.of(), "hello", ContentAvailability.AVAILABLE));
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.getFirst().chunkIndex());
    }

    private static Evidence evidence(
            String title, List<String> tags, String body, ContentAvailability availability) {
        Provenance provenance = new Provenance(
                "id:" + title,
                title,
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                tags,
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                availability);
        return new Evidence(body, provenance);
    }
}
