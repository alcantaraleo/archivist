package io.archivist.infrastructure.retrieval.embedding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class VectorStoreRegistry {

    private final Map<String, VectorStore> byName;

    VectorStoreRegistry(List<VectorStore> stores) {
        Objects.requireNonNull(stores, "stores");
        if (stores.isEmpty()) {
            throw new IllegalStateException("No VectorStore implementations registered");
        }
        Map<String, VectorStore> indexed = new LinkedHashMap<>();
        for (VectorStore store : stores) {
            Objects.requireNonNull(store, "store");
            String name = Objects.requireNonNull(store.name(), "store.name");
            VectorStore previous = indexed.put(name, store);
            if (previous != null) {
                throw new IllegalStateException("Duplicate VectorStore name '" + name + "'");
            }
        }
        this.byName = Map.copyOf(indexed);
    }

    VectorStore require(String name) {
        Objects.requireNonNull(name, "name");
        VectorStore store = byName.get(name);
        if (store == null) {
            String registered = byName.keySet().stream().sorted().collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Unknown embedding store '" + name + "'. Registered stores: [" + registered + "]");
        }
        return store;
    }

    Map<String, VectorStore> byName() {
        return byName;
    }
}
