package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindProjects;
import java.util.List;

public class FindProjectsUseCase implements FindProjects {

    @Override
    public List<Evidence> findProjects(String criteria) {
        return List.of();
    }
}
