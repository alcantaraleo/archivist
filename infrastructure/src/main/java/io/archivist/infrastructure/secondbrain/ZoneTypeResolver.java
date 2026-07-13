package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZoneTypeResolver {

    private final List<Map.Entry<String, KnowledgeZone>> sortedPrefixes;
    private final Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes;

    public ZoneTypeResolver(
            Map<String, KnowledgeZone> zonePrefixes, Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes) {
        this.sortedPrefixes = zonePrefixes.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .toList();
        this.zoneDefaultTypes = zoneDefaultTypes == null ? Map.of() : zoneDefaultTypes;
    }

    public Optional<ResolvedZoneType> resolve(String relativePath, Map<String, Object> metadata) {
        Optional<KnowledgeZone> zone = resolveZone(relativePath, metadata);
        if (zone.isEmpty()) {
            return Optional.empty();
        }
        Optional<KnowledgeType> type = resolveType(zone.get(), metadata);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedZoneType(zone.get(), type.get()));
    }

    private Optional<KnowledgeZone> resolveZone(String relativePath, Map<String, Object> metadata) {
        Object zoneField = metadata.get("zone");
        if (zoneField != null) {
            try {
                return Optional.of(KnowledgeZone.valueOf(zoneField.toString().trim().toUpperCase()));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        String normalizedPath = relativePath.replace('\\', '/');
        for (Map.Entry<String, KnowledgeZone> entry : sortedPrefixes) {
            if (normalizedPath.startsWith(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private Optional<KnowledgeType> resolveType(KnowledgeZone zone, Map<String, Object> metadata) {
        Object typeField = metadata.get("type");
        if (typeField != null) {
            try {
                return Optional.of(KnowledgeType.valueOf(typeField.toString().trim().toUpperCase()));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        KnowledgeType defaultType = zoneDefaultTypes.get(zone);
        return defaultType == null ? Optional.empty() : Optional.of(defaultType);
    }

    public record ResolvedZoneType(KnowledgeZone zone, KnowledgeType type) {
    }
}
