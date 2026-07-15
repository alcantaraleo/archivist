package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindPeopleUseCaseTest {

    @Test
    void buildsPersonTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindPeopleUseCase useCase = new FindPeopleUseCase(gateway, 20);

        useCase.findPeople("Ada");

        verify(gateway).retrieve(Query.withType("Ada", KnowledgeType.PERSON, 20));
    }
}
