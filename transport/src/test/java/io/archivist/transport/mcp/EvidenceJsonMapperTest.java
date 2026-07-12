package io.archivist.transport.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EvidenceJsonMapperTest {

    private EvidenceJsonMapper evidenceJsonMapper;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        evidenceJsonMapper = new EvidenceJsonMapper(jsonMapper);
    }

    @Test
    void shouldSerialiseEvidenceListMatchingContractFixture() throws Exception {
        Evidence sampleEvidence = new Evidence(
                "Sample retrieved knowledge text for contract verification.",
                new Provenance(
                        "contract-test-source-id",
                        "Contract Test Entry",
                        KnowledgeType.CONCEPT,
                        KnowledgeZone.SYNTHESIZED,
                        List.of("contract", "test"),
                        List.of("raw-source-id"),
                        Instant.parse("2026-01-01T12:00:00Z"),
                        Instant.parse("2026-01-02T12:00:00Z")));

        String actualJson = evidenceJsonMapper.toJson(List.of(sampleEvidence));
        JsonNode actualNode = jsonMapper.readTree(actualJson);

        Path fixturePath = Path.of("specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json");
        JsonNode expectedNode = jsonMapper.readTree(fixturePath.toFile());

        assertEquals(expectedNode, actualNode);
    }
}
