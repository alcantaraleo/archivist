package io.archivist.application.usecase;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.RetrieveContext;
import java.util.List;

public class RetrieveContextUseCase implements RetrieveContext {

    @Override
    public List<Evidence> retrieveContext(String query) {
        return List.of();
    }
}
