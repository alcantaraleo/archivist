package io.archivist.infrastructure.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RetrievalStrategyRegistry {

    private final Map<String, RetrievalStrategy> strategiesByName;
    private final RetrievalStrategy activeStrategy;

    public RetrievalStrategyRegistry(List<RetrievalStrategy> strategies, String activeStrategyName) {
        Objects.requireNonNull(strategies, "strategies");
        Objects.requireNonNull(activeStrategyName, "activeStrategyName");
        if (strategies.isEmpty()) {
            throw new IllegalStateException("No RetrievalStrategy implementations registered");
        }

        Map<String, RetrievalStrategy> indexed = new LinkedHashMap<>();
        for (RetrievalStrategy strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            String name = Objects.requireNonNull(strategy.name(), "strategy.name");
            RetrievalStrategy previous = indexed.put(name, strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate RetrievalStrategy name '" + name + "'");
            }
        }

        RetrievalStrategy active = indexed.get(activeStrategyName);
        if (active == null) {
            String registered = indexed.keySet().stream().sorted().collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Unknown active retrieval strategy '"
                            + activeStrategyName
                            + "'. Registered strategies: ["
                            + registered
                            + "]");
        }

        this.strategiesByName = Map.copyOf(indexed);
        this.activeStrategy = active;
    }

    public RetrievalStrategy getActive() {
        return activeStrategy;
    }

    public Map<String, RetrievalStrategy> strategiesByName() {
        return strategiesByName;
    }
}
