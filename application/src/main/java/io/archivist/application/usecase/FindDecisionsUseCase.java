package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindDecisions;
import java.util.List;

public class FindDecisionsUseCase implements FindDecisions {

    @Override
    public List<Evidence> findDecisions(String topic) {
        return List.of();
    }
}
