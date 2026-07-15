package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class RetrieveContextUseCaseTest {

    @Test
    void buildsUnrestrictedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        RetrieveContextUseCase useCase = new RetrieveContextUseCase(gateway, 20);

        useCase.retrieveContext("architecture");

        verify(gateway).retrieve(Query.unrestricted("architecture", 20));
    }
}
