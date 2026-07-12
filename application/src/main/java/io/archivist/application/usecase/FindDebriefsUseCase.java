package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindDebriefs;
import java.util.List;

public class FindDebriefsUseCase implements FindDebriefs {

    @Override
    public List<Evidence> findDebriefs(String topic) {
        return List.of();
    }
}
