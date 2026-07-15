package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archivist.second-brain.mapping")
public class SecondBrainMappingProperties {

    private Map<String, KnowledgeZone> zonePrefixes = defaultZonePrefixes();
    private Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes = new LinkedHashMap<>();
    private Map<String, KnowledgeType> typeAliases = defaultTypeAliases();

    public Map<String, KnowledgeZone> getZonePrefixes() {
        return zonePrefixes;
    }

    public void setZonePrefixes(Map<String, KnowledgeZone> zonePrefixes) {
        this.zonePrefixes = zonePrefixes;
    }

    public Map<KnowledgeZone, KnowledgeType> getZoneDefaultTypes() {
        return zoneDefaultTypes;
    }

    public void setZoneDefaultTypes(Map<KnowledgeZone, KnowledgeType> zoneDefaultTypes) {
        this.zoneDefaultTypes = zoneDefaultTypes;
    }

    public Map<String, KnowledgeType> getTypeAliases() {
        return typeAliases;
    }

    public void setTypeAliases(Map<String, KnowledgeType> typeAliases) {
        this.typeAliases = typeAliases;
    }

    private static Map<String, KnowledgeZone> defaultZonePrefixes() {
        Map<String, KnowledgeZone> defaults = new LinkedHashMap<>();
        defaults.put("raw/", KnowledgeZone.SOURCE);
        defaults.put("wiki/", KnowledgeZone.SYNTHESIZED);
        defaults.put("dev/", KnowledgeZone.TECHNICAL);
        defaults.put("identity/", KnowledgeZone.IDENTITY);
        defaults.put("runtime/", KnowledgeZone.COMPILED);
        defaults.put("observability/", KnowledgeZone.SIGNAL);
        return defaults;
    }

    /** Vault frontmatter types that are not exact KnowledgeType enum names. */
    private static Map<String, KnowledgeType> defaultTypeAliases() {
        Map<String, KnowledgeType> defaults = new LinkedHashMap<>();
        defaults.put("meeting-person", KnowledgeType.PERSON);
        return defaults;
    }
}
