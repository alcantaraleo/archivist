package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class LexicalRetrievalStrategy implements RetrievalStrategy {

    static final String STRATEGY_NAME = "lexical";

    private final KnowledgeCorpus knowledgeCorpus;
    private final LexicalScorer lexicalScorer;

    LexicalRetrievalStrategy(KnowledgeCorpus knowledgeCorpus) {
        this(knowledgeCorpus, new LexicalScorer());
    }

    LexicalRetrievalStrategy(KnowledgeCorpus knowledgeCorpus, LexicalScorer lexicalScorer) {
        this.knowledgeCorpus = Objects.requireNonNull(knowledgeCorpus, "knowledgeCorpus");
        this.lexicalScorer = Objects.requireNonNull(lexicalScorer, "lexicalScorer");
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public List<Evidence> retrieve(Query query) {
        Objects.requireNonNull(query, "query");
        List<String> tokens = lexicalScorer.tokenize(query.text());
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<Evidence> candidates = new ArrayList<>();
        for (Evidence evidence : knowledgeCorpus.loadAll()) {
            if (matchesFilters(evidence.provenance(), query)) {
                candidates.add(evidence);
            }
        }

        List<Evidence> ranked = lexicalScorer.rank(candidates, tokens);
        return dedupeAndLimit(ranked, query.maxResults());
    }

    private static boolean matchesFilters(Provenance provenance, Query query) {
        Set<KnowledgeType> types = query.types();
        if (!types.isEmpty() && !types.contains(provenance.type())) {
            return false;
        }
        Set<KnowledgeZone> zones = query.zones();
        if (!zones.isEmpty() && !zones.contains(provenance.zone())) {
            return false;
        }
        return true;
    }

    private static List<Evidence> dedupeAndLimit(List<Evidence> ranked, int maxResults) {
        List<Evidence> results = new ArrayList<>();
        Set<String> seenSourceIds = new HashSet<>();
        for (Evidence evidence : ranked) {
            String sourceId = evidence.provenance().sourceId();
            if (!seenSourceIds.add(sourceId)) {
                continue;
            }
            results.add(evidence);
            if (results.size() >= maxResults) {
                break;
            }
        }
        return List.copyOf(results);
    }
}
