package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindReadings;
import java.util.List;

public class FindReadingsUseCase implements FindReadings {

    @Override
    public List<Evidence> findReadings(String topic) {
        return List.of();
    }
}
