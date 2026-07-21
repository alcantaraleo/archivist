package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HybridConsensusRankerTest {

    @Test
    void ordersByConsensusScoreDescending() {
        // AC-9: score 3 > 2 > 1
        List<String> lexical = List.of("A", "B");
        List<String> bm25 = List.of("A", "B");
        List<String> embedding = List.of("A", "C");

        List<String> ranked = HybridConsensusRanker.rank(lexical, bm25, embedding, 10);

        assertEquals(List.of("A", "B", "C"), ranked);
    }

    @Test
    void tieBreakPrefersBetterEmbeddingRank() {
        // AC-10: same consensus; lower embedding index wins
        List<String> lexical = List.of("X", "Y");
        List<String> bm25 = List.of("X", "Y");
        List<String> embedding = List.of("Y", "X");

        List<String> ranked = HybridConsensusRanker.rank(lexical, bm25, embedding, 10);

        assertEquals(List.of("Y", "X"), ranked);
    }

    @Test
    void tieBreakFallsBackToBm25ThenLexical() {
        // Equal consensus (lexical+bm25); no embedding → BM25 rank decides
        List<String> lexical = List.of("b-id", "a-id");
        List<String> bm25 = List.of("a-id", "b-id");
        List<String> embedding = List.of();

        List<String> ranked = HybridConsensusRanker.rank(lexical, bm25, embedding, 10);

        assertEquals(List.of("a-id", "b-id"), ranked);
    }

    @Test
    void absentEmbeddingRankLosesToPresentEmbeddingRankAtSameScore() {
        // Both score 2 (L+B); only Y also in embedding → Y wins via embedding tie level
        List<String> lexical = List.of("X", "Y");
        List<String> bm25 = List.of("X", "Y");
        List<String> embedding = List.of("Y");

        List<String> ranked = HybridConsensusRanker.rank(lexical, bm25, embedding, 10);

        assertEquals(List.of("Y", "X"), ranked);
    }

    @Test
    void respectsEffectiveMaxCap() {
        List<String> ranked =
                HybridConsensusRanker.rank(List.of("A", "B", "C"), List.of("A"), List.of("A"), 2);

        assertEquals(List.of("A", "B"), ranked);
    }
}
