package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeGateway;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RetrievalTestConfiguration.class)
class LexicalRetrievalIntegrationTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeGateway knowledgeGateway;

    @BeforeAll
    static void copyFixtureCorpusAndAddOversizeEntry() throws Exception {
        copyResourceTree("fixture-corpus", corpusRoot);
        writeOversizeEntry(corpusRoot);
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "lexical");
        registry.add("archivist.retrieval.max-results", () -> "20");
    }

    @Test
    void shouldMatchLexicalFixtureContract() throws Exception {
        JsonNode contract = readLexicalContract();
        long started = System.nanoTime();

        for (JsonNode scenario : contract.get("scenarios")) {
            assertScenario(scenario);
        }

        assertDeduplication(contract);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        int maxDurationMs = contract.get("performance").get("maxDurationMs").intValue();
        assertTrue(
                elapsedMs < maxDurationMs,
                "Lexical fixture scenarios exceeded " + maxDurationMs + "ms (took " + elapsedMs + "ms)");
    }

    private void assertScenario(JsonNode scenario) {
        String scenarioId = scenario.get("id").stringValue();
        Query query = toQuery(scenario.get("query"));
        List<Evidence> results = knowledgeGateway.retrieve(query);

        if (scenario.has("exactResultCount")) {
            assertEquals(
                    scenario.get("exactResultCount").intValue(),
                    results.size(),
                    scenarioId + " exactResultCount");
        }
        if (scenario.has("minResultCount")) {
            assertTrue(
                    results.size() >= scenario.get("minResultCount").intValue(),
                    scenarioId + " minResultCount");
        }
        if (scenario.has("mustIncludeSourceIds")) {
            Set<String> actualIds = sourceIds(results);
            for (JsonNode expectedId : scenario.get("mustIncludeSourceIds")) {
                assertTrue(
                        actualIds.contains(expectedId.stringValue()),
                        scenarioId + " missing " + expectedId.stringValue());
            }
        }
        if (scenario.has("mustNotIncludeSourceIds")) {
            Set<String> actualIds = sourceIds(results);
            for (JsonNode forbiddenId : scenario.get("mustNotIncludeSourceIds")) {
                assertFalse(
                        actualIds.contains(forbiddenId.stringValue()),
                        scenarioId + " unexpectedly includes " + forbiddenId.stringValue());
            }
        }
        if (scenario.has("allResultsType")) {
            KnowledgeType expected = KnowledgeType.valueOf(scenario.get("allResultsType").stringValue());
            for (Evidence evidence : results) {
                assertEquals(expected, evidence.provenance().type(), scenarioId);
            }
        }
        if (scenario.has("allResultsTypes")) {
            Set<KnowledgeType> allowed = scenario.get("allResultsTypes").valueStream()
                    .map(node -> KnowledgeType.valueOf(node.stringValue()))
                    .collect(Collectors.toSet());
            for (Evidence evidence : results) {
                assertTrue(allowed.contains(evidence.provenance().type()), scenarioId);
            }
        }
        if (scenario.has("mustNotIncludeTypes")) {
            Set<KnowledgeType> forbidden = scenario.get("mustNotIncludeTypes").valueStream()
                    .map(node -> KnowledgeType.valueOf(node.stringValue()))
                    .collect(Collectors.toSet());
            for (Evidence evidence : results) {
                assertFalse(forbidden.contains(evidence.provenance().type()), scenarioId);
            }
        }
        if (scenario.has("firstResultSourceId") && !results.isEmpty()) {
            assertEquals(
                    scenario.get("firstResultSourceId").stringValue(),
                    results.getFirst().provenance().sourceId(),
                    scenarioId + " firstResultSourceId");
        }
        if (scenario.has("allResultsMustMatchTermIn")) {
            String term = query.text().toLowerCase(Locale.ROOT);
            for (Evidence evidence : results) {
                assertTrue(matchesAnyField(evidence, term), scenarioId + " term match for " + term);
            }
        }
        if (scenario.has("includedEntryContentEmpty")
                && scenario.get("includedEntryContentEmpty").booleanValue()) {
            for (JsonNode expectedId : scenario.get("mustIncludeSourceIds")) {
                Evidence match = requireEvidence(results, expectedId.stringValue(), scenarioId);
                assertEquals("", match.content(), scenarioId);
            }
        }
        if (scenario.has("includedEntryAvailability")) {
            ContentAvailability expected =
                    ContentAvailability.valueOf(scenario.get("includedEntryAvailability").stringValue());
            for (JsonNode expectedId : scenario.get("mustIncludeSourceIds")) {
                Evidence match = requireEvidence(results, expectedId.stringValue(), scenarioId);
                assertEquals(expected, match.provenance().contentAvailability(), scenarioId);
            }
        }
    }

    private void assertDeduplication(JsonNode contract) {
        Query query = Query.unrestricted("sample", 20);
        List<Evidence> results = knowledgeGateway.retrieve(query);
        Set<String> unique = new HashSet<>();
        for (Evidence evidence : results) {
            assertTrue(
                    unique.add(evidence.provenance().sourceId()),
                    "Duplicate sourceId in results: " + evidence.provenance().sourceId());
        }
        assertTrue(contract.get("deduplication").has("acceptanceCriteria"));
    }

    private static Query toQuery(JsonNode queryNode) {
        String factory = queryNode.get("factory").stringValue();
        String text = queryNode.get("text").stringValue();
        int maxResults = queryNode.get("maxResults").intValue();
        return switch (factory) {
            case "unrestricted" -> Query.unrestricted(text, maxResults);
            case "withType" -> Query.withType(
                    text, KnowledgeType.valueOf(queryNode.get("type").stringValue()), maxResults);
            case "withTypes" -> Query.withTypes(
                    text,
                    queryNode.get("types").valueStream()
                            .map(node -> KnowledgeType.valueOf(node.stringValue()))
                            .collect(Collectors.toSet()),
                    maxResults);
            default -> throw new IllegalArgumentException("Unknown factory: " + factory);
        };
    }

    private static Evidence requireEvidence(List<Evidence> results, String sourceId, String scenarioId) {
        return results.stream()
                .filter(evidence -> evidence.provenance().sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(scenarioId + " missing included entry " + sourceId));
    }

    private static Set<String> sourceIds(List<Evidence> results) {
        return results.stream()
                .map(evidence -> evidence.provenance().sourceId())
                .collect(Collectors.toSet());
    }

    private static boolean matchesAnyField(Evidence evidence, String term) {
        String title = evidence.provenance().title().toLowerCase(Locale.ROOT);
        if (title.contains(term)) {
            return true;
        }
        if (evidence.content() != null && evidence.content().toLowerCase(Locale.ROOT).contains(term)) {
            return true;
        }
        return evidence.provenance().tags().stream()
                .anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(term));
    }

    private static JsonNode readLexicalContract() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        Path fixturePath = Path.of("specs/004-lexical-retrieval/contracts/fixture-lexical-expected.json");
        return jsonMapper.readTree(fixturePath.toFile());
    }

    private static void copyResourceTree(String resourceRoot, Path targetRoot) throws Exception {
        URL resourceUrl =
                LexicalRetrievalIntegrationTest.class.getClassLoader().getResource(resourceRoot);
        Path sourceRoot = Path.of(resourceUrl.toURI());
        try (var paths = Files.walk(sourceRoot)) {
            paths.forEach(sourcePath -> {
                Path destination = targetRoot.resolve(sourceRoot.relativize(sourcePath));
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(sourcePath, destination);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
    }

    private static void writeOversizeEntry(Path root) throws IOException {
        Path oversizePath = root.resolve("edge-cases/oversize-entry.md");
        Files.createDirectories(oversizePath.getParent());
        String frontmatter =
                """
                ---
                title: oversize-fixture-title
                type: concept
                zone: SYNTHESIZED
                created: 2026-01-01T00:00:00Z
                updated: 2026-01-02T00:00:00Z
                ---

                unique-body-only-token-xyz
                """;
        char[] padding = new char[1_048_576];
        Arrays.fill(padding, 'x');
        Files.writeString(oversizePath, frontmatter + new String(padding));
    }
}
