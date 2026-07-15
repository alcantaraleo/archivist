package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindDecisions;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class FindDecisionsUseCase implements FindDecisions {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindDecisionsUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findDecisions(String topic) {
        return gateway.retrieve(Query.withType(topic, KnowledgeType.DECISION, defaultMaxResults));
    }
}
