package io.archivist.infrastructure.secondbrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataEnvelopeParserTest {

    private final MetadataEnvelopeParser parser = new MetadataEnvelopeParser();

    @Test
    void shouldSplitYamlEnvelopeFromMarkdownBody() {
        String text =
                """
                ---
                title: Sample
                type: concept
                ---
                Body text for sample reading.
                """;

        MetadataEnvelopeParser.ParseResult result = parser.parse(text);

        assertEquals("Sample", result.metadata().get("title"));
        assertEquals("concept", result.metadata().get("type"));
        assertEquals("Body text for sample reading.", result.body());
    }

    @Test
    void shouldStripBlankLineAfterClosingDelimiter() {
        String text =
                """
                ---
                title: Sample
                type: concept
                ---

                Body text for sample reading.
                """;

        MetadataEnvelopeParser.ParseResult result = parser.parse(text);

        assertEquals("Body text for sample reading.", result.body());
    }

    @Test
    void shouldParseMetadataFromPrefixBuffer() {
        String text =
                """
                ---
                title: Oversize Entry
                type: concept
                ---
                """;

        MetadataEnvelopeParser.ParseResult result = parser.parseBytes(text.getBytes(), text.length());

        assertEquals("Oversize Entry", result.metadata().get("title"));
        assertEquals("concept", result.metadata().get("type"));
        assertTrue(result.body().isEmpty());
    }

    @Test
    void shouldReturnEmptyMetadataWhenNoEnvelopePresent() {
        MetadataEnvelopeParser.ParseResult result = parser.parse("Plain markdown without frontmatter.");

        assertTrue(result.metadata().isEmpty());
        assertEquals("Plain markdown without frontmatter.", result.body());
    }

    @Test
    void shouldReturnEmptyMapsForBlankInput() {
        MetadataEnvelopeParser.ParseResult result = parser.parse("   ");

        assertEquals(Map.of(), result.metadata());
        assertEquals("", result.body());
    }
}
