package io.archivist.domain.port.out;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import java.util.List;

public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
