package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CorpusEntryMapper {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    private final SourceIdNormalizer sourceIdNormalizer;
    private final ZoneTypeResolver zoneTypeResolver;

    public CorpusEntryMapper(SourceIdNormalizer sourceIdNormalizer, ZoneTypeResolver zoneTypeResolver) {
        this.sourceIdNormalizer = sourceIdNormalizer;
        this.zoneTypeResolver = zoneTypeResolver;
    }

    public Optional<MappedEntry> map(
            Path corpusRoot,
            Path filePath,
            Map<String, Object> metadata,
            String body,
            ContentAvailability availability,
            Instant fileModifiedTime) {
        String relativePath = corpusRoot.relativize(filePath).toString().replace('\\', '/');
        Optional<ZoneTypeResolver.ResolvedZoneType> resolved = zoneTypeResolver.resolve(relativePath, metadata);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }

        String sourceId = sourceIdNormalizer.normalize(corpusRoot, filePath, metadata);
        String title = resolveTitle(metadata, body, filePath);
        List<String> tags = resolveStringList(metadata.get("tags"));
        List<String> sources = resolveSources(metadata.get("sources"));
        Instant created = resolveInstant(metadata.get("created"), fileModifiedTime);
        Instant updated = resolveInstant(metadata.get("updated"), fileModifiedTime);

        Provenance provenance = new Provenance(
                sourceId,
                title,
                resolved.get().type(),
                resolved.get().zone(),
                tags,
                sources,
                created,
                updated,
                availability);

        String content = availability == ContentAvailability.AVAILABLE ? body : "";
        return Optional.of(new MappedEntry(provenance, content));
    }

    private String resolveTitle(Map<String, Object> metadata, String body, Path filePath) {
        Object titleField = metadata.get("title");
        if (titleField != null && !titleField.toString().isBlank()) {
            return titleField.toString().trim();
        }
        Matcher matcher = HEADING_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(".md")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    private List<String> resolveStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            List<String> tags = new ArrayList<>();
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    tags.add(item.toString().trim());
                }
            }
            return List.copyOf(tags);
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.contains(",")) {
            return List.of(text.split("\\s*,\\s*"));
        }
        return List.of(text);
    }

    private List<String> resolveSources(Object value) {
        return resolveStringList(value);
    }

    private Instant resolveInstant(Object value, Instant fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Instant.parse(value.toString().trim());
        } catch (DateTimeParseException exception) {
            return fallback;
        }
    }

    public record MappedEntry(Provenance provenance, String content) {

        public Evidence toEvidence() {
            return new Evidence(content, provenance);
        }
    }
}
