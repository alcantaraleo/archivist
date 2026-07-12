package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindConcepts;
import java.util.List;

public class FindConceptsUseCase implements FindConcepts {

    @Override
    public List<Evidence> findConcepts(String topic) {
        return List.of();
    }
}
