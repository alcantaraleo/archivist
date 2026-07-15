package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindProjectsUseCaseTest {

    @Test
    void buildsProjectTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindProjectsUseCase useCase = new FindProjectsUseCase(gateway, 20);

        useCase.findProjects("archivist");

        verify(gateway).retrieve(Query.withType("archivist", KnowledgeType.PROJECT, 20));
    }
}
