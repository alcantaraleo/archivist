package io.archivist.domain.port.in;

import io.archivist.domain.model.Evidence;
import java.util.List;

public interface FindConcepts {
    List<Evidence> findConcepts(String topic);
}
