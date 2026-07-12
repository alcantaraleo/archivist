package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindPeople;
import java.util.List;

public class FindPeopleUseCase implements FindPeople {

    @Override
    public List<Evidence> findPeople(String name) {
        return List.of();
    }
}
