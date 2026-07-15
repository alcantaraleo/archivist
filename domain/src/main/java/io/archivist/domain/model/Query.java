package io.archivist.domain.model;

import java.util.Objects;
import java.util.Set;

public record Query(
        String text, Set<KnowledgeType> types, Set<KnowledgeZone> zones, int maxResults) {

    public Query {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(zones, "zones");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        types = Set.copyOf(types);
        zones = Set.copyOf(zones);
    }

    public static Query unrestricted(String text, int maxResults) {
        return new Query(text, Set.of(), Set.of(), maxResults);
    }

    public static Query withType(String text, KnowledgeType type, int maxResults) {
        return new Query(text, Set.of(type), Set.of(), maxResults);
    }

    public static Query withTypes(String text, Set<KnowledgeType> types, int maxResults) {
        return new Query(text, types, Set.of(), maxResults);
    }
}
