package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindDebriefs;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class FindDebriefsUseCase implements FindDebriefs {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindDebriefsUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findDebriefs(String topic) {
        return gateway.retrieve(Query.withType(topic, KnowledgeType.DEBRIEF, defaultMaxResults));
    }
}
