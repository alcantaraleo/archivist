package io.archivist.transport.mcp;

import io.archivist.domain.port.in.FindConcepts;
import io.archivist.domain.port.in.FindDebriefs;
import io.archivist.domain.port.in.FindDecisions;
import io.archivist.domain.port.in.FindPeople;
import io.archivist.domain.port.in.FindProjects;
import io.archivist.domain.port.in.FindReadings;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import io.archivist.domain.port.in.RetrieveContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ArchivistMcpTools {

    private final RetrieveContext retrieveContext;
    private final FindDecisions findDecisions;
    private final FindProjects findProjects;
    private final FindPeople findPeople;
    private final FindConcepts findConcepts;
    private final FindRelatedKnowledge findRelatedKnowledge;
    private final FindReadings findReadings;
    private final FindDebriefs findDebriefs;
    private final EvidenceJsonMapper evidenceJsonMapper;

    public ArchivistMcpTools(
            RetrieveContext retrieveContext,
            FindDecisions findDecisions,
            FindProjects findProjects,
            FindPeople findPeople,
            FindConcepts findConcepts,
            FindRelatedKnowledge findRelatedKnowledge,
            FindReadings findReadings,
            FindDebriefs findDebriefs,
            EvidenceJsonMapper evidenceJsonMapper) {
        this.retrieveContext = retrieveContext;
        this.findDecisions = findDecisions;
        this.findProjects = findProjects;
        this.findPeople = findPeople;
        this.findConcepts = findConcepts;
        this.findRelatedKnowledge = findRelatedKnowledge;
        this.findReadings = findReadings;
        this.findDebriefs = findDebriefs;
        this.evidenceJsonMapper = evidenceJsonMapper;
    }

    @McpTool(
            name = "retrieveContext",
            description = "General contextual retrieval across the full knowledge base")
    public String retrieveContext(
            @McpToolParam(description = "The retrieval query", required = true) String query) {
        McpInputValidator.requireNonBlank(query, "query");
        return evidenceJsonMapper.toJson(retrieveContext.retrieveContext(query));
    }

    @McpTool(
            name = "findDecisions",
            description = "Retrieve architecture and product decisions (ADRs)")
    public String findDecisions(
            @McpToolParam(description = "The topic or context to search decisions for", required = true)
                    String topic) {
        McpInputValidator.requireNonBlank(topic, "topic");
        return evidenceJsonMapper.toJson(findDecisions.findDecisions(topic));
    }

    @McpTool(
            name = "findProjects",
            description = "Retrieve active or past projects and initiatives")
    public String findProjects(
            @McpToolParam(description = "Search criteria for projects", required = true) String criteria) {
        McpInputValidator.requireNonBlank(criteria, "criteria");
        return evidenceJsonMapper.toJson(findProjects.findProjects(criteria));
    }

    @McpTool(name = "findPeople", description = "Retrieve known individuals with professional context")
    public String findPeople(
            @McpToolParam(description = "Name or identifier of the person", required = true) String name) {
        McpInputValidator.requireNonBlank(name, "name");
        return evidenceJsonMapper.toJson(findPeople.findPeople(name));
    }

    @McpTool(
            name = "findConcepts",
            description = "Retrieve technical and professional concepts and cross-cutting insights")
    public String findConcepts(
            @McpToolParam(description = "The concept or topic to retrieve", required = true) String topic) {
        McpInputValidator.requireNonBlank(topic, "topic");
        return evidenceJsonMapper.toJson(findConcepts.findConcepts(topic));
    }

    @McpTool(
            name = "findRelatedKnowledge",
            description = "Graph-traversal retrieval of semantically connected knowledge")
    public String findRelatedKnowledge(
            @McpToolParam(description = "Starting point for knowledge graph traversal", required = true)
                    String query) {
        McpInputValidator.requireNonBlank(query, "query");
        return evidenceJsonMapper.toJson(findRelatedKnowledge.findRelatedKnowledge(query));
    }

    @McpTool(
            name = "findReadings",
            description = "Retrieve source materials: articles, transcripts, book notes")
    public String findReadings(
            @McpToolParam(description = "Topic to search readings for", required = true) String topic) {
        McpInputValidator.requireNonBlank(topic, "topic");
        return evidenceJsonMapper.toJson(findReadings.findReadings(topic));
    }

    @McpTool(
            name = "findDebriefs",
            description = "Retrieve incident retrospectives and learning reviews")
    public String findDebriefs(
            @McpToolParam(description = "Topic or incident context", required = true) String topic) {
        McpInputValidator.requireNonBlank(topic, "topic");
        return evidenceJsonMapper.toJson(findDebriefs.findDebriefs(topic));
    }
}
