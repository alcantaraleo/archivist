package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindConcepts;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FindConceptsUseCase implements FindConcepts {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindConceptsUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findConcepts(String topic) {
        return gateway.retrieve(Query.withTypes(
                topic, Set.of(KnowledgeType.CONCEPT, KnowledgeType.SYNTHESIS), defaultMaxResults));
    }
}
