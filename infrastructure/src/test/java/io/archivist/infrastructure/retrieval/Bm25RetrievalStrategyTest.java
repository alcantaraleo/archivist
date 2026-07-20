package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Bm25RetrievalStrategyTest {

    @Test
    void blankQueryReturnsEmptyList() {
        KnowledgeCorpus corpus = stubCorpus(List.of(evidence(
                "wiki/concept-a",
                "fixture concept",
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                "fixture body")));
        Bm25RetrievalStrategy strategy = new Bm25RetrievalStrategy(
                corpus, new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties()), new Bm25Properties());

        assertTrue(strategy.retrieve(Query.unrestricted("   ", 5)).isEmpty());
        assertTrue(strategy.retrieve(Query.unrestricted("\t\n", 5)).isEmpty());
    }

    @Test
    void zoneFilterExcludesOtherZones() {
        Evidence synthesized = evidence(
                "wiki/concept-a",
                "zone filter token",
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                "zone filter token body");
        Evidence technical = evidence(
                "dev/decision-a",
                "zone filter token",
                KnowledgeType.DECISION,
                KnowledgeZone.TECHNICAL,
                "zone filter token body");

        KnowledgeCorpus corpus = stubCorpus(List.of(synthesized, technical));
        Bm25RetrievalStrategy strategy = new Bm25RetrievalStrategy(
                corpus, new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties()), new Bm25Properties());

        List<Evidence> results = strategy.retrieve(
                new Query("zone filter token", Set.of(), Set.of(KnowledgeZone.TECHNICAL), 10));

        assertEquals(1, results.size());
        assertEquals("dev/decision-a", results.getFirst().provenance().sourceId());
        assertEquals(KnowledgeZone.TECHNICAL, results.getFirst().provenance().zone());
    }

    @Test
    void typeFilterAndMaxResultsAndDedupe() {
        Evidence decision = evidence(
                "dev/decision-a",
                "fixture decision alpha",
                KnowledgeType.DECISION,
                KnowledgeZone.TECHNICAL,
                "fixture alpha body");
        Evidence duplicate = evidence(
                "dev/decision-a",
                "fixture decision alpha duplicate",
                KnowledgeType.DECISION,
                KnowledgeZone.TECHNICAL,
                "fixture alpha body duplicate");
        Evidence concept = evidence(
                "wiki/concept-a",
                "fixture concept",
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                "fixture alpha concept body");

        KnowledgeCorpus corpus = stubCorpus(List.of(decision, duplicate, concept));
        Bm25RetrievalStrategy strategy = new Bm25RetrievalStrategy(
                corpus, new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties()), new Bm25Properties());

        List<Evidence> results = strategy.retrieve(Query.withType("fixture", KnowledgeType.DECISION, 1));

        assertEquals(1, results.size());
        assertEquals(KnowledgeType.DECISION, results.getFirst().provenance().type());
        assertEquals("dev/decision-a", results.getFirst().provenance().sourceId());
    }

    @Test
    void oversizeEntryMatchesTitleNotBodyOnlyToken() {
        Evidence oversize = evidence(
                "edge/oversize",
                "unique-oversize-title-token",
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                "unique-body-only-token");
        oversize = new Evidence(
                "",
                new Provenance(
                        oversize.provenance().sourceId(),
                        oversize.provenance().title(),
                        oversize.provenance().type(),
                        oversize.provenance().zone(),
                        oversize.provenance().tags(),
                        oversize.provenance().sources(),
                        oversize.provenance().created(),
                        oversize.provenance().updated(),
                        ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE));

        KnowledgeCorpus corpus = stubCorpus(List.of(oversize));
        Bm25RetrievalStrategy strategy = new Bm25RetrievalStrategy(
                corpus, new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties()), new Bm25Properties());

        assertEquals(1, strategy.retrieve(Query.unrestricted("unique-oversize-title-token", 5)).size());
        assertTrue(strategy.retrieve(Query.unrestricted("unique-body-only-token", 5)).isEmpty());
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
            String sourceId, String title, KnowledgeType type, KnowledgeZone zone, String body) {
        Provenance provenance = new Provenance(
                sourceId,
                title,
                type,
                zone,
                List.of("fixture"),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                ContentAvailability.AVAILABLE);
        return new Evidence(body, provenance);
    }
}
