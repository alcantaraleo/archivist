package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Multi-leg consensus strategy. Delegates to lexical, BM25, and embedding beans — never
 * {@link RetrievalStrategyRegistry#getActive()}. Policy: ADR-0006 / hybrid-consensus-algorithm.md.
 */
final class HybridRetrievalStrategy implements RetrievalStrategy {

    static final String STRATEGY_NAME = "hybrid";

    private final RetrievalStrategy lexical;
    private final RetrievalStrategy bm25;
    private final RetrievalStrategy embedding;
    private final HybridProperties properties;

    HybridRetrievalStrategy(
            RetrievalStrategy lexical,
            RetrievalStrategy bm25,
            RetrievalStrategy embedding,
            HybridProperties properties) {
        this.lexical = Objects.requireNonNull(lexical, "lexical");
        this.bm25 = Objects.requireNonNull(bm25, "bm25");
        this.embedding = Objects.requireNonNull(embedding, "embedding");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (STRATEGY_NAME.equals(lexical.name())
                || STRATEGY_NAME.equals(bm25.name())
                || STRATEGY_NAME.equals(embedding.name())) {
            throw new IllegalArgumentException("hybrid legs must not be the hybrid strategy itself");
        }
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Evidence> retrieve(Query query) {
        Objects.requireNonNull(query, "query");
        String text = query.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        CompletableFuture<List<Evidence>> lexicalFuture =
                CompletableFuture.supplyAsync(() -> lexical.retrieve(query));
        CompletableFuture<List<Evidence>> bm25Future =
                CompletableFuture.supplyAsync(() -> bm25.retrieve(query));
        CompletableFuture<List<Evidence>> embeddingFuture =
                CompletableFuture.supplyAsync(() -> embedding.retrieve(query));

        List<Evidence> lexicalHits;
        List<Evidence> bm25Hits;
        List<Evidence> embeddingHits;
        try {
            CompletableFuture.allOf(lexicalFuture, bm25Future, embeddingFuture).join();
            lexicalHits = lexicalFuture.join();
            bm25Hits = bm25Future.join();
            embeddingHits = embeddingFuture.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("hybrid leg retrieval failed", cause);
        }

        int effectiveMax = Math.min(query.maxResults(), properties.getMaxResults());
        List<String> orderedIds = HybridConsensusRanker.rank(
                sourceIds(lexicalHits), sourceIds(bm25Hits), sourceIds(embeddingHits), effectiveMax);

        Map<String, Evidence> byPriority = assembleByLegPriority(lexicalHits, bm25Hits, embeddingHits);
        List<Evidence> results = new ArrayList<>(orderedIds.size());
        for (String sourceId : orderedIds) {
            Evidence evidence = byPriority.get(sourceId);
            if (evidence != null) {
                results.add(evidence);
            }
        }
        return List.copyOf(results);
    }

    /** embedding → BM25 → lexical; later put overwrites so embedding wins. */
    private static Map<String, Evidence> assembleByLegPriority(
            List<Evidence> lexicalHits, List<Evidence> bm25Hits, List<Evidence> embeddingHits) {
        Map<String, Evidence> assembled = new HashMap<>();
        putAll(assembled, lexicalHits);
        putAll(assembled, bm25Hits);
        putAll(assembled, embeddingHits);
        return assembled;
    }

    private static void putAll(Map<String, Evidence> target, List<Evidence> hits) {
        for (Evidence evidence : hits) {
            target.put(evidence.provenance().sourceId(), evidence);
        }
    }

    private static List<String> sourceIds(List<Evidence> hits) {
        List<String> ids = new ArrayList<>(hits.size());
        for (Evidence evidence : hits) {
            ids.add(evidence.provenance().sourceId());
        }
        return ids;
    }
}
