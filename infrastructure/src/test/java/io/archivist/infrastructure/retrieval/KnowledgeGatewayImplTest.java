package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeGatewayImplTest {

    private RetrievalStrategy activeStrategy;
    private KnowledgeGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        activeStrategy = mock(RetrievalStrategy.class);
        when(activeStrategy.name()).thenReturn("lexical");
        RetrievalStrategyRegistry registry =
                new RetrievalStrategyRegistry(List.of(activeStrategy), "lexical");
        gateway = new KnowledgeGatewayImpl(registry);
    }

    @Test
    void blankTextReturnsEmptyWithoutDelegating() {
        List<Evidence> results = gateway.retrieve(Query.unrestricted("   ", 20));

        assertTrue(results.isEmpty());
        verify(activeStrategy, never()).retrieve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullQueryThrows() {
        assertThrows(NullPointerException.class, () -> gateway.retrieve(null));
    }

    @Test
    void delegatesToActiveStrategy() {
        Query query = Query.unrestricted("sample", 20);
        Evidence evidence = new Evidence(
                "body",
                new Provenance(
                        "wiki/concepts/sample-concept",
                        "Sample Concept",
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ContentAvailability.AVAILABLE));
        when(activeStrategy.retrieve(query)).thenReturn(List.of(evidence));

        List<Evidence> results = gateway.retrieve(query);

        assertEquals(List.of(evidence), results);
        verify(activeStrategy).retrieve(query);
    }
}
