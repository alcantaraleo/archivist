package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Instant;
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.junit.jupiter.api.Test;

class Bm25IndexBuilderTest {

    @Test
    void duplicateSourceIdKeepsLastEvidenceOnly() throws Exception {
        Evidence first = evidence(
                "wiki/dup",
                "First Title",
                "first-body-token",
                List.of("tag"),
                ContentAvailability.AVAILABLE);
        Evidence second = evidence(
                "wiki/dup",
                "Second Title",
                "second-body-token",
                List.of("tag"),
                ContentAvailability.AVAILABLE);

        try (Bm25IndexBuilder.BuiltIndex built =
                new Bm25IndexBuilder().build(stubCorpus(List.of(first, second)), new Bm25Properties())) {
            assertEquals(1, built.evidenceBySourceId().size());
            assertEquals("Second Title", built.evidenceBySourceId().get("wiki/dup").provenance().title());
            assertEquals(1, built.reader().numDocs());
        }
    }

    @Test
    void indexesTitleAndTagsAndOmitsOversizeBody() throws Exception {
        Evidence available = evidence(
                "wiki/ok",
                "Alpha Title",
                "alpha-body-token",
                List.of("alpha-tag"),
                ContentAvailability.AVAILABLE);
        Evidence oversize = evidence(
                "wiki/large",
                "Beta Title",
                "beta-body-only",
                List.of("beta-tag"),
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE);

        KnowledgeCorpus corpus = stubCorpus(List.of(available, oversize));
        Bm25Properties properties = new Bm25Properties();

        try (Bm25IndexBuilder.BuiltIndex built = new Bm25IndexBuilder().build(corpus, properties)) {
            IndexSearcher searcher = new IndexSearcher(built.reader());
            assertEquals(2, built.evidenceBySourceId().size());

            TopDocs titleHit = searcher.search(new TermQuery(new Term(Bm25IndexBuilder.FIELD_TITLE, "beta")), 10);
            assertTrue(titleHit.scoreDocs.length >= 1);

            TopDocs bodyHit = searcher.search(new TermQuery(new Term(Bm25IndexBuilder.FIELD_BODY, "beta")), 10);
            assertEquals(0, bodyHit.scoreDocs.length);
        }
    }

    private static KnowledgeCorpus stubCorpus(List<Evidence> entries) {
        return new KnowledgeCorpus() {
            @Override
            public List<Provenance> catalog() {
                return entries.stream().map(Evidence::provenance).toList();
            }

            @Override
            public java.util.Optional<Evidence> loadBySourceId(String sourceId) {
                return entries.stream()
                        .filter(entry -> entry.provenance().sourceId().equals(sourceId))
                        .findFirst();
            }

            @Override
            public List<Evidence> loadAll() {
                return entries;
            }
        };
    }

    private static Evidence evidence(
            String sourceId,
            String title,
            String body,
            List<String> tags,
            ContentAvailability availability) {
        Provenance provenance = new Provenance(
                sourceId,
                title,
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                tags,
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                availability);
        String content = availability == ContentAvailability.AVAILABLE ? body : "";
        return new Evidence(content, provenance);
    }
}
