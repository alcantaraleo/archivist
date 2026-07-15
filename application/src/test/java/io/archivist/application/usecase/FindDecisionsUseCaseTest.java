package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindDecisionsUseCaseTest {

    @Test
    void buildsDecisionTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindDecisionsUseCase useCase = new FindDecisionsUseCase(gateway, 20);

        useCase.findDecisions("caching");

        verify(gateway).retrieve(Query.withType("caching", KnowledgeType.DECISION, 20));
    }
}
