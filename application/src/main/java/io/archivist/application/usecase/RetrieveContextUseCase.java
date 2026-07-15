package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.in.RetrieveContext;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import java.util.Objects;

public class RetrieveContextUseCase implements RetrieveContext {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public RetrieveContextUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> retrieveContext(String query) {
        return gateway.retrieve(Query.unrestricted(query, defaultMaxResults));
    }
}
