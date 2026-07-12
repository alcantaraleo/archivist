package io.archivist.domain.port.in;

import io.archivist.domain.model.Evidence;
import java.util.List;

public interface FindProjects {
    List<Evidence> findProjects(String criteria);
}
