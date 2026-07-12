package io.archivist.domain.port.in;

import io.archivist.domain.model.Evidence;
import java.util.List;

public interface FindRelatedKnowledge {
    List<Evidence> findRelatedKnowledge(String query);
}
