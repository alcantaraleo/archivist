package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import java.util.List;

public class FindRelatedKnowledgeUseCase implements FindRelatedKnowledge {

    @Override
    public List<Evidence> findRelatedKnowledge(String query) {
        return List.of();
    }
}
