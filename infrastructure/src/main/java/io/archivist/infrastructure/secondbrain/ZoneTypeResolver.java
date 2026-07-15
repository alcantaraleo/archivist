package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ZoneTypeResolver {

    private final List<Map.Entry<String, KnowledgeZone>> sortedPrefixes;
    private final Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes;
    private final Map<String, KnowledgeType> typeAliases;

    public ZoneTypeResolver(
            Map<String, KnowledgeZone> zonePrefixes, Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes) {
        this(zonePrefixes, zoneDefaultTypes, Map.of());
    }

    public ZoneTypeResolver(
            Map<String, KnowledgeZone> zonePrefixes,
            Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes,
            Map<String, KnowledgeType> typeAliases) {
        this.sortedPrefixes = zonePrefixes.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .toList();
        this.zoneDefaultTypes = zoneDefaultTypes == null ? Map.of() : zoneDefaultTypes;
        this.typeAliases = typeAliases == null ? Map.of() : typeAliases;
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
        // Prefix match is case-insensitive so Title Case vault folders (Wiki/, Identity/)
        // still map when defaults are lowercase (wiki/, identity/).
        String normalizedPath = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (Map.Entry<String, KnowledgeZone> entry : sortedPrefixes) {
            String prefix = entry.getKey().toLowerCase(Locale.ROOT);
            if (normalizedPath.startsWith(prefix)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private Optional<KnowledgeType> resolveType(KnowledgeZone zone, Map<String, Object> metadata) {
        Object typeField = metadata.get("type");
        if (typeField != null) {
            String rawType = typeField.toString().trim();
            String aliasKey = rawType.toLowerCase(Locale.ROOT);
            KnowledgeType aliased = typeAliases.get(aliasKey);
            if (aliased != null) {
                return Optional.of(aliased);
            }
            try {
                return Optional.of(KnowledgeType.valueOf(rawType.toUpperCase(Locale.ROOT)));
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
