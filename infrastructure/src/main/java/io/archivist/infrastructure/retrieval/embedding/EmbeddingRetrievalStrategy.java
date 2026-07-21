package io.archivist.infrastructure.retrieval.embedding;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.retrieval.RetrievalStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EmbeddingRetrievalStrategy implements RetrievalStrategy {

    static final String STRATEGY_NAME = "embedding";

    private final KnowledgeCorpus knowledgeCorpus;
    private final TextEmbedder embedder;
    private final EmbeddingIndexCache indexCache;
    private final EmbeddingProperties properties;

    public EmbeddingRetrievalStrategy(
            KnowledgeCorpus knowledgeCorpus,
            TextEmbedder embedder,
            EmbeddingIndexCache indexCache,
            EmbeddingProperties properties) {
        this.knowledgeCorpus = Objects.requireNonNull(knowledgeCorpus, "knowledgeCorpus");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        this.indexCache = Objects.requireNonNull(indexCache, "indexCache");
        this.properties = Objects.requireNonNull(properties, "properties");
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

        float[] queryVector = embedder.embed(text);
        int searchTopK = effectiveSearchTopK(query);
        List<ScoredChunk> candidates = indexCache.withStore(
                knowledgeCorpus, store -> store.search(queryVector, searchTopK));

        Float minScore = properties.getMinScore();
        Map<String, Float> bestScoreBySourceId = new HashMap<>();
        for (ScoredChunk scored : candidates) {
            if (!matchesFilters(scored.chunk().knowledgeType(), scored.chunk().knowledgeZone(), query)) {
                continue;
            }
            if (minScore != null && scored.score() < minScore) {
                continue;
            }
            bestScoreBySourceId.merge(scored.chunk().sourceId(), scored.score(), Math::max);
        }

        List<Map.Entry<String, Float>> ranked = new ArrayList<>(bestScoreBySourceId.entrySet());
        ranked.sort(Comparator.<Map.Entry<String, Float>>comparingDouble(Map.Entry::getValue).reversed());

        List<Evidence> results = new ArrayList<>();
        for (Map.Entry<String, Float> entry : ranked) {
            Evidence evidence = knowledgeCorpus.loadBySourceId(entry.getKey()).orElse(null);
            if (evidence != null) {
                results.add(evidence);
            }
            if (results.size() >= query.maxResults()) {
                break;
            }
        }
        return List.copyOf(results);
    }

    /**
     * When type/zone filters apply after vector search, over-fetch chunk candidates (same idea as BM25
     * fetch window) so filtered entries are not dropped because unrelated chunks filled {@code topK}.
     */
    private int effectiveSearchTopK(Query query) {
        int configured = properties.getTopK();
        boolean hasFilters = !query.types().isEmpty() || !query.zones().isEmpty();
        if (!hasFilters) {
            return configured;
        }
        int expanded = Math.max(query.maxResults() * 10, 50);
        return Math.min(Math.max(configured, expanded), 500);
    }

    private static boolean matchesFilters(KnowledgeType type, KnowledgeZone zone, Query query) {
        Set<KnowledgeType> types = query.types();
        if (!types.isEmpty() && !types.contains(type)) {
            return false;
        }
        Set<KnowledgeZone> zones = query.zones();
        if (!zones.isEmpty() && !zones.contains(zone)) {
            return false;
        }
        return true;
    }
}
