package io.archivist.infrastructure.retrieval;

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

class LexicalScorerTest {

    private final LexicalScorer scorer = new LexicalScorer();

    @Test
    void tokenizeIsCaseInsensitiveAndSplitsPunctuation() {
        assertEquals(List.of("hello", "world", "topic"), scorer.tokenize("Hello, WORLD! topic"));
    }

    @Test
    void titleMatchOutranksBodyMatch() {
        Evidence titleHit = evidence("a", "Sample Topic", "unrelated body", List.of());
        Evidence bodyHit = evidence("b", "Unrelated", "mentions sample elsewhere", List.of());

        List<String> tokens = scorer.tokenize("sample");
        assertTrue(scorer.score(titleHit, tokens) > scorer.score(bodyHit, tokens));
    }

    @Test
    void multiTermScoringSumsPerMatchingSignal() {
        Evidence evidence = evidence(
                "id",
                "Sample Concept",
                "sample body text",
                List.of("sample-tag"));
        List<String> tokens = scorer.tokenize("sample");

        // title×3 + tag×2 + body×1
        assertEquals(6, scorer.score(evidence, tokens));
    }

    @Test
    void rankUsesSourceIdTieBreak() {
        Evidence first = evidence("wiki/a", "Sample", "x", List.of());
        Evidence second = evidence("wiki/b", "Sample", "x", List.of());

        List<Evidence> ranked = scorer.rank(List.of(second, first), scorer.tokenize("sample"));
        assertEquals("wiki/a", ranked.getFirst().provenance().sourceId());
    }

    private static Evidence evidence(String sourceId, String title, String content, List<String> tags) {
        Provenance provenance = new Provenance(
                sourceId,
                title,
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                tags,
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                ContentAvailability.AVAILABLE);
        return new Evidence(content, provenance);
    }
}
