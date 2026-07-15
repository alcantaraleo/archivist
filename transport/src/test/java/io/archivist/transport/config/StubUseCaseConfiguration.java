package io.archivist.transport.config;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.port.in.FindConcepts;
import io.archivist.domain.port.in.FindDebriefs;
import io.archivist.domain.port.in.FindDecisions;
import io.archivist.domain.port.in.FindPeople;
import io.archivist.domain.port.in.FindProjects;
import io.archivist.domain.port.in.FindReadings;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import io.archivist.domain.port.in.RetrieveContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test-only empty {@code port.in} beans for MCP adapter contract tests.
 * Production wiring comes from infrastructure auto-configuration.
 */
@Configuration
public class StubUseCaseConfiguration {

    @Bean
    public RetrieveContext retrieveContext() {
        return query -> List.<Evidence>of();
    }

    @Bean
    public FindDecisions findDecisions() {
        return topic -> List.of();
    }

    @Bean
    public FindProjects findProjects() {
        return criteria -> List.of();
    }

    @Bean
    public FindPeople findPeople() {
        return name -> List.of();
    }

    @Bean
    public FindConcepts findConcepts() {
        return topic -> List.of();
    }

    @Bean
    public FindRelatedKnowledge findRelatedKnowledge() {
        return query -> List.of();
    }

    @Bean
    public FindReadings findReadings() {
        return topic -> List.of();
    }

    @Bean
    public FindDebriefs findDebriefs() {
        return topic -> List.of();
    }
}
