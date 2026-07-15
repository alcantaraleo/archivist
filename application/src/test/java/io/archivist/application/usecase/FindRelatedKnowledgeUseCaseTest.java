package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindRelatedKnowledgeUseCaseTest {

    @Test
    void buildsUnrestrictedQueryAsLexicalFallback() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindRelatedKnowledgeUseCase useCase = new FindRelatedKnowledgeUseCase(gateway, 20);

        useCase.findRelatedKnowledge("linked ideas");

        verify(gateway).retrieve(Query.unrestricted("linked ideas", 20));
    }
}
