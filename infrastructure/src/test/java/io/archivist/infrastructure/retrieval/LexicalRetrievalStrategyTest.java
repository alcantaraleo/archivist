package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LexicalRetrievalStrategyTest {

    private KnowledgeCorpus knowledgeCorpus;
    private LexicalRetrievalStrategy strategy;

    @BeforeEach
    void setUp() {
        knowledgeCorpus = mock(KnowledgeCorpus.class);
        strategy = new LexicalRetrievalStrategy(knowledgeCorpus);
    }

    @Test
    void typeFilterExcludesWrongTypes() {
        when(knowledgeCorpus.loadAll())
                .thenReturn(List.of(
                        available("dev/decisions/one", "Fixture Decision", KnowledgeType.DECISION, "body"),
                        available("wiki/concepts/one", "Fixture Concept", KnowledgeType.CONCEPT, "body")));

        List<Evidence> results =
                strategy.retrieve(Query.withType("fixture", KnowledgeType.DECISION, 20));

        assertEquals(1, results.size());
        assertEquals(KnowledgeType.DECISION, results.getFirst().provenance().type());
    }

    @Test
    void oversizeTitleMatchIsIncludedWithEmptyBody() {
        Evidence oversize = unavailable(
                "edge-cases/oversize-entry", "oversize-fixture-title", "unique-body-only-token-xyz");
        when(knowledgeCorpus.loadAll()).thenReturn(List.of(oversize));

        List<Evidence> results =
                strategy.retrieve(Query.unrestricted("oversize-fixture-title", 20));

        assertEquals(1, results.size());
        assertEquals("", results.getFirst().content());
        assertEquals(
                ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE,
                results.getFirst().provenance().contentAvailability());
    }

    @Test
    void oversizeBodyOnlyTokenIsExcluded() {
        Evidence oversize = unavailable(
                "edge-cases/oversize-entry", "oversize-fixture-title", "unique-body-only-token-xyz");
        when(knowledgeCorpus.loadAll()).thenReturn(List.of(oversize));

        List<Evidence> results =
                strategy.retrieve(Query.unrestricted("unique-body-only-token-xyz", 20));

        assertTrue(results.isEmpty());
    }

    @Test
    void deduplicatesBySourceId() {
        Evidence first = available("wiki/concepts/one", "Sample", KnowledgeType.CONCEPT, "sample");
        Evidence duplicate = available("wiki/concepts/one", "Sample", KnowledgeType.CONCEPT, "sample again");
        when(knowledgeCorpus.loadAll()).thenReturn(List.of(first, duplicate));

        List<Evidence> results = strategy.retrieve(Query.unrestricted("sample", 20));

        assertEquals(1, results.size());
    }

    @Test
    void respectsMaxResultsCap() {
        when(knowledgeCorpus.loadAll())
                .thenReturn(List.of(
                        available("a", "Sample A", KnowledgeType.CONCEPT, "x"),
                        available("b", "Sample B", KnowledgeType.CONCEPT, "x"),
                        available("c", "Sample C", KnowledgeType.CONCEPT, "x")));

        List<Evidence> results = strategy.retrieve(Query.unrestricted("sample", 2));

        assertEquals(2, results.size());
    }

    @Test
    void nameIsLexical() {
        assertEquals("lexical", strategy.name());
    }

    private static Evidence available(String sourceId, String title, KnowledgeType type, String content) {
        return new Evidence(
                content,
                new Provenance(
                        sourceId,
                        title,
                        type,
                        KnowledgeZone.SYNTHESIZED,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.AVAILABLE));
    }

    private static Evidence unavailable(String sourceId, String title, String unusedBody) {
        return new Evidence(
                "",
                new Provenance(
                        sourceId,
                        title,
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE));
    }
}
