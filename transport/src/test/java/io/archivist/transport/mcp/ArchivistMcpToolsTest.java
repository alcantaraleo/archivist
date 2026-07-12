package io.archivist.transport.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.in.FindConcepts;
import io.archivist.domain.port.in.FindDebriefs;
import io.archivist.domain.port.in.FindDecisions;
import io.archivist.domain.port.in.FindPeople;
import io.archivist.domain.port.in.FindProjects;
import io.archivist.domain.port.in.FindReadings;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import io.archivist.domain.port.in.RetrieveContext;
import io.archivist.transport.support.McpAdapterTestConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = McpAdapterTestConfiguration.class)
@TestPropertySource(properties = {"spring.ai.mcp.server.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivistMcpToolsTest {

    @Autowired
    private ArchivistMcpTools archivistMcpTools;

    @MockitoBean
    private RetrieveContext retrieveContext;

    @MockitoBean
    private FindDecisions findDecisions;

    @MockitoBean
    private FindProjects findProjects;

    @MockitoBean
    private FindPeople findPeople;

    @MockitoBean
    private FindConcepts findConcepts;

    @MockitoBean
    private FindRelatedKnowledge findRelatedKnowledge;

    @MockitoBean
    private FindReadings findReadings;

    @MockitoBean
    private FindDebriefs findDebriefs;

    @ParameterizedTest(name = "{0} rejects blank input")
    @MethodSource("toolInvocations")
    void shouldRejectInvalidInputWithoutDelegating(
            String toolName, Function<ArchivistMcpTools, String> invocation, String paramName) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> invocation.apply(archivistMcpTools));

        assertEquals(
                "Parameter '" + paramName + "' must not be null, empty, or blank",
                exception.getMessage());
        verifyNoPortInInteraction();
    }

    @ParameterizedTest(name = "{0} delegates valid input")
    @MethodSource("validToolInvocations")
    void shouldDelegateValidInputAndReturnEmptyJson(
            String toolName, Function<ArchivistMcpTools, String> invocation, Object portInMock) {
        stubEmptyResponse(portInMock);

        String response = invocation.apply(archivistMcpTools);

        assertEquals("[]", response);
        verifyPortInCalled(portInMock);
    }

    @Test
    void shouldSerialiseSampleEvidenceViaProductionMapperPath() throws Exception {
        Evidence sampleEvidence = sampleEvidence();
        when(retrieveContext.retrieveContext("architecture")).thenReturn(List.of(sampleEvidence));

        String response = archivistMcpTools.retrieveContext("architecture");

        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        JsonNode actualNode = jsonMapper.readTree(response);
        JsonNode expectedNode = jsonMapper.readTree(Path.of(
                        "specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json")
                .toFile());

        assertEquals(expectedNode, actualNode);
        verify(retrieveContext).retrieveContext("architecture");
    }

    @Test
    void shouldSurfaceDelegationFailure() {
        when(findDecisions.findDecisions(anyString())).thenThrow(new RuntimeException("retrieval failed"));

        assertThrows(RuntimeException.class, () -> archivistMcpTools.findDecisions("topic"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void shouldRejectInvalidRetrieveContextInput(String value) {
        assertThrows(IllegalArgumentException.class, () -> archivistMcpTools.retrieveContext(value));
        verify(retrieveContext, never()).retrieveContext(anyString());
    }

    static Stream<Arguments> toolInvocations() {
        return Stream.of(
                Arguments.of("retrieveContext", (Function<ArchivistMcpTools, String>) tools -> tools.retrieveContext(" "), "query"),
                Arguments.of("findDecisions", (Function<ArchivistMcpTools, String>) tools -> tools.findDecisions(" "), "topic"),
                Arguments.of("findProjects", (Function<ArchivistMcpTools, String>) tools -> tools.findProjects(" "), "criteria"),
                Arguments.of("findPeople", (Function<ArchivistMcpTools, String>) tools -> tools.findPeople(" "), "name"),
                Arguments.of("findConcepts", (Function<ArchivistMcpTools, String>) tools -> tools.findConcepts(" "), "topic"),
                Arguments.of("findRelatedKnowledge", (Function<ArchivistMcpTools, String>) tools -> tools.findRelatedKnowledge(" "), "query"),
                Arguments.of("findReadings", (Function<ArchivistMcpTools, String>) tools -> tools.findReadings(" "), "topic"),
                Arguments.of("findDebriefs", (Function<ArchivistMcpTools, String>) tools -> tools.findDebriefs(" "), "topic"));
    }

    Stream<Arguments> validToolInvocations() {
        return Stream.of(
                Arguments.of("retrieveContext", (Function<ArchivistMcpTools, String>) tools -> tools.retrieveContext("query"), retrieveContext),
                Arguments.of("findDecisions", (Function<ArchivistMcpTools, String>) tools -> tools.findDecisions("topic"), findDecisions),
                Arguments.of("findProjects", (Function<ArchivistMcpTools, String>) tools -> tools.findProjects("criteria"), findProjects),
                Arguments.of("findPeople", (Function<ArchivistMcpTools, String>) tools -> tools.findPeople("Ada"), findPeople),
                Arguments.of("findConcepts", (Function<ArchivistMcpTools, String>) tools -> tools.findConcepts("topic"), findConcepts),
                Arguments.of("findRelatedKnowledge", (Function<ArchivistMcpTools, String>) tools -> tools.findRelatedKnowledge("query"), findRelatedKnowledge),
                Arguments.of("findReadings", (Function<ArchivistMcpTools, String>) tools -> tools.findReadings("topic"), findReadings),
                Arguments.of("findDebriefs", (Function<ArchivistMcpTools, String>) tools -> tools.findDebriefs("topic"), findDebriefs));
    }

    private void stubEmptyResponse(Object portInMock) {
        if (portInMock instanceof RetrieveContext retrieveContextMock) {
            when(retrieveContextMock.retrieveContext(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindDecisions findDecisionsMock) {
            when(findDecisionsMock.findDecisions(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindProjects findProjectsMock) {
            when(findProjectsMock.findProjects(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindPeople findPeopleMock) {
            when(findPeopleMock.findPeople(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindConcepts findConceptsMock) {
            when(findConceptsMock.findConcepts(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindRelatedKnowledge findRelatedKnowledgeMock) {
            when(findRelatedKnowledgeMock.findRelatedKnowledge(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindReadings findReadingsMock) {
            when(findReadingsMock.findReadings(anyString())).thenReturn(List.of());
        } else if (portInMock instanceof FindDebriefs findDebriefsMock) {
            when(findDebriefsMock.findDebriefs(anyString())).thenReturn(List.of());
        }
    }

    private void verifyPortInCalled(Object portInMock) {
        if (portInMock instanceof RetrieveContext retrieveContextMock) {
            verify(retrieveContextMock).retrieveContext(anyString());
        } else if (portInMock instanceof FindDecisions findDecisionsMock) {
            verify(findDecisionsMock).findDecisions(anyString());
        } else if (portInMock instanceof FindProjects findProjectsMock) {
            verify(findProjectsMock).findProjects(anyString());
        } else if (portInMock instanceof FindPeople findPeopleMock) {
            verify(findPeopleMock).findPeople(anyString());
        } else if (portInMock instanceof FindConcepts findConceptsMock) {
            verify(findConceptsMock).findConcepts(anyString());
        } else if (portInMock instanceof FindRelatedKnowledge findRelatedKnowledgeMock) {
            verify(findRelatedKnowledgeMock).findRelatedKnowledge(anyString());
        } else if (portInMock instanceof FindReadings findReadingsMock) {
            verify(findReadingsMock).findReadings(anyString());
        } else if (portInMock instanceof FindDebriefs findDebriefsMock) {
            verify(findDebriefsMock).findDebriefs(anyString());
        }
    }

    private void verifyNoPortInInteraction() {
        verify(retrieveContext, never()).retrieveContext(anyString());
        verify(findDecisions, never()).findDecisions(anyString());
        verify(findProjects, never()).findProjects(anyString());
        verify(findPeople, never()).findPeople(anyString());
        verify(findConcepts, never()).findConcepts(anyString());
        verify(findRelatedKnowledge, never()).findRelatedKnowledge(anyString());
        verify(findReadings, never()).findReadings(anyString());
        verify(findDebriefs, never()).findDebriefs(anyString());
    }

    private static Evidence sampleEvidence() {
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
                        Instant.parse("2026-01-02T12:00:00Z")));
    }
}
