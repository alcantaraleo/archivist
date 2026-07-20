package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

final class Bm25RetrievalStrategy implements RetrievalStrategy {

    static final String STRATEGY_NAME = "bm25";

    private final KnowledgeCorpus knowledgeCorpus;
    private final Bm25IndexCache indexCache;
    private final Bm25Properties properties;

    Bm25RetrievalStrategy(KnowledgeCorpus knowledgeCorpus, Bm25IndexCache indexCache, Bm25Properties properties) {
        this.knowledgeCorpus = Objects.requireNonNull(knowledgeCorpus, "knowledgeCorpus");
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
        List<String> tokens = RetrievalTokenization.tokenize(query.text());
        if (tokens.isEmpty()) {
            return List.of();
        }

        return indexCache.withIndex(knowledgeCorpus, cachedIndex -> search(cachedIndex, query, tokens));
    }

    private List<Evidence> search(
            Bm25IndexCache.CachedIndex cachedIndex, Query query, List<String> tokens) {
        IndexSearcher searcher = new IndexSearcher(cachedIndex.builtIndex().reader());
        searcher.setSimilarity(new BM25Similarity(properties.getK1(), properties.getB()));

        org.apache.lucene.search.Query luceneQuery = buildLuceneQuery(query, tokens);
        int fetchLimit = Math.min(Math.max(query.maxResults() * 10, 50), 500);
        TopDocs topDocs;
        try {
            topDocs = searcher.search(luceneQuery, fetchLimit);
        } catch (IOException exception) {
            throw new IllegalStateException("BM25 search failed", exception);
        }

        List<Evidence> ranked = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            try {
                String sourceId = searcher
                        .storedFields()
                        .document(scoreDoc.doc)
                        .get(Bm25IndexBuilder.FIELD_SOURCE_ID);
                Evidence evidence = cachedIndex.builtIndex().evidenceBySourceId().get(sourceId);
                if (evidence != null) {
                    ranked.add(evidence);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read BM25 hit", exception);
            }
        }
        return dedupeAndLimit(ranked, query.maxResults());
    }

    private org.apache.lucene.search.Query buildLuceneQuery(Query query, List<String> tokens) {
        BooleanQuery.Builder root = new BooleanQuery.Builder();

        for (String token : tokens) {
            // DisjunctionMax (tie=0): best field wins per token; query-time BoostQuery applies 3:2:1.
            // (Lucene 9 removed Field.setBoost; lexical instead sums field weights.)
            List<org.apache.lucene.search.Query> fieldQueries = new ArrayList<>();
            fieldQueries.add(boostedTerm(Bm25IndexBuilder.FIELD_TITLE, token, properties.getFieldBoostTitle()));
            fieldQueries.add(boostedTerm(Bm25IndexBuilder.FIELD_TAGS, token, properties.getFieldBoostTags()));
            fieldQueries.add(boostedTerm(Bm25IndexBuilder.FIELD_BODY, token, properties.getFieldBoostBody()));
            root.add(new DisjunctionMaxQuery(fieldQueries, 0.0f), BooleanClause.Occur.MUST);
        }

        appendEnumFilter(root, Bm25IndexBuilder.FIELD_KNOWLEDGE_TYPE, query.types());
        appendEnumFilter(root, Bm25IndexBuilder.FIELD_KNOWLEDGE_ZONE, query.zones());

        return root.build();
    }

    private static BoostQuery boostedTerm(String field, String token, float boost) {
        TermQuery termQuery = new TermQuery(new Term(field, token));
        return new BoostQuery(termQuery, boost);
    }

    private static void appendEnumFilter(
            BooleanQuery.Builder root, String field, Set<? extends Enum<?>> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        BooleanQuery.Builder filter = new BooleanQuery.Builder();
        for (Enum<?> value : allowed) {
            filter.add(new TermQuery(new Term(field, value.name())), BooleanClause.Occur.SHOULD);
        }
        filter.setMinimumNumberShouldMatch(1);
        root.add(filter.build(), BooleanClause.Occur.MUST);
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
