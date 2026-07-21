package io.archivist.infrastructure.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure consensus fusion for hybrid retrieval. Infrastructure-private — scores and ranks never leave
 * this package. Algorithm: {@code specs/007-hybrid-retrieval/contracts/hybrid-consensus-algorithm.md}.
 */
final class HybridConsensusRanker {

    private HybridConsensusRanker() {}

    /**
     * Rank distinct sourceIds by consensus score descending, then embedding → BM25 → lexical rank
     * (lower index better; absent = worse), then sourceId ascending.
     *
     * @param lexical ordered sourceIds from lexical leg
     * @param bm25 ordered sourceIds from BM25 leg
     * @param embedding ordered sourceIds from embedding leg
     * @param effectiveMax truncate after sort; must be ≥ 1
     */
    static List<String> rank(
            List<String> lexical, List<String> bm25, List<String> embedding, int effectiveMax) {
        Objects.requireNonNull(lexical, "lexical");
        Objects.requireNonNull(bm25, "bm25");
        Objects.requireNonNull(embedding, "embedding");
        if (effectiveMax < 1) {
            throw new IllegalArgumentException("effectiveMax must be >= 1");
        }

        Map<String, Integer> lexicalRank = rankIndex(lexical);
        Map<String, Integer> bm25Rank = rankIndex(bm25);
        Map<String, Integer> embeddingRank = rankIndex(embedding);

        Set<String> candidates = new HashSet<>();
        candidates.addAll(lexicalRank.keySet());
        candidates.addAll(bm25Rank.keySet());
        candidates.addAll(embeddingRank.keySet());

        List<String> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt((String id) -> -consensusScore(id, lexicalRank, bm25Rank, embeddingRank))
                .thenComparingInt(id -> tieRank(embeddingRank.getOrDefault(id, -1)))
                .thenComparingInt(id -> tieRank(bm25Rank.getOrDefault(id, -1)))
                .thenComparingInt(id -> tieRank(lexicalRank.getOrDefault(id, -1)))
                .thenComparing(id -> id));

        if (ordered.size() > effectiveMax) {
            return List.copyOf(ordered.subList(0, effectiveMax));
        }
        return List.copyOf(ordered);
    }

    private static int consensusScore(
            String id,
            Map<String, Integer> lexicalRank,
            Map<String, Integer> bm25Rank,
            Map<String, Integer> embeddingRank) {
        int score = 0;
        if (lexicalRank.containsKey(id)) {
            score++;
        }
        if (bm25Rank.containsKey(id)) {
            score++;
        }
        if (embeddingRank.containsKey(id)) {
            score++;
        }
        return score;
    }

    /** Absent rank (−1) sorts after any present rank for ascending tie comparison. */
    private static int tieRank(int rank) {
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }

    private static Map<String, Integer> rankIndex(List<String> orderedIds) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            String id = orderedIds.get(i);
            ranks.putIfAbsent(id, i);
        }
        return ranks;
    }
}
