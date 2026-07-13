package io.archivist.infrastructure.secondbrain;

import java.nio.file.Path;
import java.util.Map;

public class SourceIdNormalizer {

    public String normalize(Path corpusRoot, Path filePath, Map<String, Object> metadata) {
        Object id = metadata.get("id");
        if (id != null && !id.toString().isBlank()) {
            return id.toString().trim();
        }
        String relativePath = corpusRoot.relativize(filePath).toString().replace('\\', '/');
        if (relativePath.endsWith(".md")) {
            return relativePath.substring(0, relativePath.length() - 3);
        }
        return relativePath;
    }
}
