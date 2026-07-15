package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public final class KnowledgeGatewayImpl implements KnowledgeGateway {

    private final RetrievalStrategyRegistry registry;

    public KnowledgeGatewayImpl(RetrievalStrategyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public List<Evidence> retrieve(Query query) {
        Objects.requireNonNull(query, "query");
        if (query.text().isBlank()) {
            return List.of();
        }
        return registry.getActive().retrieve(query);
    }
}
