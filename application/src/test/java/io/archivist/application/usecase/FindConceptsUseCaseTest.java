package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FindConceptsUseCaseTest {

    @Test
    void buildsConceptAndSynthesisTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindConceptsUseCase useCase = new FindConceptsUseCase(gateway, 20);

        useCase.findConcepts("sample");

        verify(gateway)
                .retrieve(Query.withTypes(
                        "sample", Set.of(KnowledgeType.CONCEPT, KnowledgeType.SYNTHESIS), 20));
    }
}
