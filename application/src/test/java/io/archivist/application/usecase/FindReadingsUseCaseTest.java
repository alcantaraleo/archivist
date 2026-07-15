package io.archivist.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import org.junit.jupiter.api.Test;

class FindReadingsUseCaseTest {

    @Test
    void buildsReadingTypedQuery() {
        KnowledgeGateway gateway = mock(KnowledgeGateway.class);
        FindReadingsUseCase useCase = new FindReadingsUseCase(gateway, 20);

        useCase.findReadings("paper");

        verify(gateway).retrieve(Query.withType("paper", KnowledgeType.READING, 20));
    }
}
