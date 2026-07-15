package io.archivist.transport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TransportLayerIsolationTest {

    @Test
    void mainSourcesMustNotImportInfrastructure() throws IOException {
        Path mainSources = Path.of("transport/src/main/java");
        assertTrue(Files.isDirectory(mainSources), "Expected transport main sources at " + mainSources);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(mainSources)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int index = 0; index < lines.size(); index++) {
                        String line = lines.get(index).trim();
                        if (line.startsWith("import io.archivist.infrastructure")) {
                            violations.add(path + ":" + (index + 1) + ": " + line);
                        }
                    }
                } catch (IOException exception) {
                    fail("Failed reading " + path + ": " + exception.getMessage());
                }
            });
        }

        if (!violations.isEmpty()) {
            fail("Transport main sources must not import infrastructure:\n" + String.join("\n", violations));
        }
    }
}
