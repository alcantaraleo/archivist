package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.FindPeople;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class FindPeopleUseCase implements FindPeople {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindPeopleUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findPeople(String name) {
        return gateway.retrieve(Query.withType(name, KnowledgeType.PERSON, defaultMaxResults));
    }
}
