package io.archivist.infrastructure.secondbrain;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class MetadataEnvelopeParser {

    private static final String DELIMITER = "---";

    public ParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParseResult(Collections.emptyMap(), "");
        }
        if (!text.startsWith(DELIMITER)) {
            return new ParseResult(Collections.emptyMap(), text);
        }

        int yamlStart = text.indexOf('\n', DELIMITER.length());
        if (yamlStart < 0) {
            return new ParseResult(Collections.emptyMap(), "");
        }
        yamlStart++;

        int closeDelimiter = text.indexOf("\n---", yamlStart);
        if (closeDelimiter < 0) {
            return new ParseResult(parseYamlMap(text.substring(yamlStart)), "");
        }

        String yamlPart = text.substring(yamlStart, closeDelimiter);
        int bodyStart = closeDelimiter + "\n---".length();
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\r') {
            bodyStart++;
        }
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        String body = bodyStart < text.length() ? text.substring(bodyStart).strip() : "";
        return new ParseResult(parseYamlMap(yamlPart), body);
    }

    public ParseResult parseBytes(byte[] data, int length) {
        return parse(new String(data, 0, length, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlMap(String yamlPart) {
        if (yamlPart.isBlank()) {
            return Collections.emptyMap();
        }
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(yamlPart);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    public record ParseResult(Map<String, Object> metadata, String body) {
    }
}
