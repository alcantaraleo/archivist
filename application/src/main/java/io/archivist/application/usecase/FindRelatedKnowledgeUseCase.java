package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class FindRelatedKnowledgeUseCase implements FindRelatedKnowledge {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindRelatedKnowledgeUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findRelatedKnowledge(String query) {
        return gateway.retrieve(Query.unrestricted(query, defaultMaxResults));
    }
}
