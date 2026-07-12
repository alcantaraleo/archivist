package io.archivist.domain.port.in;

import io.archivist.domain.model.Evidence;
import java.util.List;

public interface FindDecisions {
    List<Evidence> findDecisions(String topic);
}
