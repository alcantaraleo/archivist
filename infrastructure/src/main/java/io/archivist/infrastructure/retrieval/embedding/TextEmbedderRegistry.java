package io.archivist.infrastructure.retrieval.embedding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TextEmbedderRegistry {

    private final Map<String, TextEmbedder> byName;

    TextEmbedderRegistry(List<TextEmbedder> embedders) {
        Objects.requireNonNull(embedders, "embedders");
        if (embedders.isEmpty()) {
            throw new IllegalStateException("No TextEmbedder implementations registered");
        }
        Map<String, TextEmbedder> indexed = new LinkedHashMap<>();
        for (TextEmbedder embedder : embedders) {
            Objects.requireNonNull(embedder, "embedder");
            String name = Objects.requireNonNull(embedder.name(), "embedder.name");
            TextEmbedder previous = indexed.put(name, embedder);
            if (previous != null) {
                throw new IllegalStateException("Duplicate TextEmbedder name '" + name + "'");
            }
        }
        this.byName = Map.copyOf(indexed);
    }

    TextEmbedder require(String name) {
        Objects.requireNonNull(name, "name");
        TextEmbedder embedder = byName.get(name);
        if (embedder == null) {
            String registered = byName.keySet().stream().sorted().collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Unknown embedding embedder '" + name + "'. Registered embedders: [" + registered + "]");
        }
        return embedder;
    }

    Map<String, TextEmbedder> byName() {
        return byName;
    }
}
