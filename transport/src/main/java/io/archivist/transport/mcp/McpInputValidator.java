package io.archivist.transport.mcp;

public final class McpInputValidator {

    private McpInputValidator() {
    }

    public static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Parameter '" + paramName + "' must not be null, empty, or blank");
        }
    }
}
