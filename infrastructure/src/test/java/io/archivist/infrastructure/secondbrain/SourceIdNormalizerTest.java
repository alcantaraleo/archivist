package io.archivist.infrastructure.secondbrain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceIdNormalizerTest {

    private final SourceIdNormalizer normalizer = new SourceIdNormalizer();

    @Test
    void shouldDeriveSourceIdFromRelativePathWithoutExtension() {
        Path corpusRoot = Path.of("/corpus");
        Path filePath = Path.of("/corpus/wiki/concepts/sample-concept.md");

        String sourceId = normalizer.normalize(corpusRoot, filePath, Map.of());

        assertEquals("wiki/concepts/sample-concept", sourceId);
    }

    @Test
    void shouldUseMetadataIdOverrideWhenPresent() {
        Path corpusRoot = Path.of("/corpus");
        Path filePath = Path.of("/corpus/wiki/concepts/sample-concept.md");

        String sourceId = normalizer.normalize(corpusRoot, filePath, Map.of("id", "custom-source-id"));

        assertEquals("custom-source-id", sourceId);
    }
}
