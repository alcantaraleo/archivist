package io.archivist.infrastructure.secondbrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZoneTypeResolverTest {

    private final ZoneTypeResolver resolver = new ZoneTypeResolver(defaultZonePrefixes(), Map.of());

    @Test
    void shouldMapPathPrefixToZoneAndMetadataType() {
        var resolved = resolver.resolve("wiki/concepts/sample-concept", Map.of("type", "concept"));

        assertTrue(resolved.isPresent());
        assertEquals(KnowledgeZone.SYNTHESIZED, resolved.get().zone());
        assertEquals(KnowledgeType.CONCEPT, resolved.get().type());
    }

    @Test
    void shouldPreferLongestMatchingPrefix() {
        Map<String, KnowledgeZone> prefixes = new LinkedHashMap<>();
        prefixes.put("wiki/", KnowledgeZone.SYNTHESIZED);
        prefixes.put("wiki/people/", KnowledgeZone.IDENTITY);
        ZoneTypeResolver customResolver = new ZoneTypeResolver(prefixes, Map.of());

        var resolved = customResolver.resolve("wiki/people/sample-person", Map.of("type", "person"));

        assertTrue(resolved.isPresent());
        assertEquals(KnowledgeZone.IDENTITY, resolved.get().zone());
    }

    @Test
    void shouldMatchZonePrefixCaseInsensitively() {
        var resolved = resolver.resolve("Wiki/People/Victor.md", Map.of("type", "person"));

        assertTrue(resolved.isPresent());
        assertEquals(KnowledgeZone.SYNTHESIZED, resolved.get().zone());
        assertEquals(KnowledgeType.PERSON, resolved.get().type());
    }

    @Test
    void shouldAliasMeetingPersonToPerson() {
        ZoneTypeResolver aliased = new ZoneTypeResolver(
                defaultZonePrefixes(),
                Map.of(),
                Map.of("meeting-person", KnowledgeType.PERSON));

        var resolved = aliased.resolve("Wiki/People/Victor.md", Map.of("type", "meeting-person"));

        assertTrue(resolved.isPresent());
        assertEquals(KnowledgeType.PERSON, resolved.get().type());
    }

    @Test
    void shouldReturnEmptyForUnknownType() {
        var resolved = resolver.resolve("wiki/concepts/unknown", Map.of("type", "NOT_A_REAL_TYPE"));

        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldUseMetadataZoneOverride() {
        var resolved = resolver.resolve(
                "wiki/concepts/sample", Map.of("type", "concept", "zone", "technical"));

        assertTrue(resolved.isPresent());
        assertEquals(KnowledgeZone.TECHNICAL, resolved.get().zone());
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
}
