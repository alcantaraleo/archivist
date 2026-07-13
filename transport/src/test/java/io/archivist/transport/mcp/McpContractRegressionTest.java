package io.archivist.transport.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.transport.support.McpAdapterTestConfiguration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = McpAdapterTestConfiguration.class)
@TestPropertySource(
        properties = {
            "spring.ai.mcp.server.enabled=false",
            "spring.ai.mcp.server.name=archivist",
            "spring.ai.mcp.server.version=0.1.0"
        })
class McpContractRegressionTest {

    @Autowired
    private List<SyncToolSpecification> syncToolSpecifications;

    @Autowired
    private EvidenceJsonMapper evidenceJsonMapper;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${spring.ai.mcp.server.name}")
    private String serverName;

    @Value("${spring.ai.mcp.server.version}")
    private String serverVersion;

    @Test
    void shouldMatchRegisteredToolsAndEvidenceShapeAgainstFixtures() throws Exception {
        List<ToolCallback> toolCallbacks = toToolCallbacks(syncToolSpecifications);

        assertEquals(8, toolCallbacks.size());

        JsonNode expectedTools = readFixture("mcp-tools-expected.json").get("tools");
        List<JsonNode> actualTools = toolCallbacks.stream()
                .map(this::toComparableToolNode)
                .sorted(Comparator.comparing(node -> node.get("name").stringValue()))
                .toList();

        assertEquals(expectedTools.size(), actualTools.size());
        for (JsonNode expectedTool : expectedTools) {
            JsonNode actualTool = actualTools.stream()
                    .filter(node -> node.get("name").stringValue().equals(expectedTool.get("name").stringValue()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(expectedTool, actualTool, "Tool contract mismatch for " + expectedTool.get("name"));
        }

        JsonNode expectedIdentity = readFixture("mcp-server-identity-expected.json");
        assertEquals(expectedIdentity.get("name").stringValue(), serverName);
        assertEquals(expectedIdentity.get("version").stringValue(), serverVersion);

        String serialisedEvidence = evidenceJsonMapper.toJson(List.of(sampleEvidence()));
        JsonNode actualEvidence = jsonMapper.readTree(serialisedEvidence);
        JsonNode expectedEvidence = readFixture("evidence-response-schema-expected.json");
        assertEquals(expectedEvidence, actualEvidence);
    }

    private List<ToolCallback> toToolCallbacks(List<SyncToolSpecification> specifications) {
        return specifications.stream().<ToolCallback>map(this::toToolCallback).toList();
    }

    private ToolCallback toToolCallback(SyncToolSpecification specification) {
        McpSchema.Tool tool = specification.tool();
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(toInputSchemaJson(tool.inputSchema()))
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                return "[]";
            }
        };
    }

    private JsonNode toComparableToolNode(ToolCallback callback) {
        try {
            JsonNode inputSchema = normalizeInputSchema(jsonMapper.readTree(callback.getToolDefinition().inputSchema()));
            return jsonMapper
                    .createObjectNode()
                    .put("name", callback.getToolDefinition().name())
                    .put("description", callback.getToolDefinition().description())
                    .set("inputSchema", inputSchema);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to parse tool input schema", exception);
        }
    }

    private JsonNode normalizeInputSchema(JsonNode inputSchema) {
        if (inputSchema.isObject() && inputSchema.has("$schema")) {
            return ((tools.jackson.databind.node.ObjectNode) inputSchema).without("$schema");
        }
        return inputSchema;
    }

    private String toInputSchemaJson(java.util.Map<String, Object> inputSchema) {
        try {
            return jsonMapper.writeValueAsString(inputSchema);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise input schema", exception);
        }
    }

    private JsonNode readFixture(String fileName) throws Exception {
        Path fixturePath = Path.of("specs/002-mcp-transport-adapter/contracts", fileName);
        return jsonMapper.readTree(fixturePath.toFile());
    }

    private Evidence sampleEvidence() {
        return new Evidence(
                "Sample retrieved knowledge text for contract verification.",
                new Provenance(
                        "contract-test-source-id",
                        "Contract Test Entry",
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of("contract", "test"),
                        List.of("raw-source-id"),
                        Instant.parse("2026-01-01T12:00:00Z"),
                        Instant.parse("2026-01-02T12:00:00Z"),
                        ContentAvailability.AVAILABLE));
    }
}
