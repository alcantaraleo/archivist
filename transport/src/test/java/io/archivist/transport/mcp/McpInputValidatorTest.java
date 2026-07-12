package io.archivist.transport.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class McpInputValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    void shouldRejectNullEmptyOrWhitespace(String value) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> McpInputValidator.requireNonBlank(value, "query"));

        assertEquals("Parameter 'query' must not be null, empty, or blank", exception.getMessage());
    }

    @Test
    void shouldAcceptNonBlankValue() {
        assertDoesNotThrow(() -> McpInputValidator.requireNonBlank("valid query", "query"));
    }
}
