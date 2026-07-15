package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindProjects;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class FindProjectsUseCase implements FindProjects {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindProjectsUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findProjects(String criteria) {
        return gateway.retrieve(Query.withType(criteria, KnowledgeType.PROJECT, defaultMaxResults));
    }
}
