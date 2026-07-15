package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindDebriefsUseCaseTest {

    @Test
    void buildsDebriefTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindDebriefsUseCase useCase = new FindDebriefsUseCase(gateway, 20);

        useCase.findDebriefs("outage");

        verify(gateway).retrieve(Query.withType("outage", KnowledgeType.DEBRIEF, 20));
    }
}
