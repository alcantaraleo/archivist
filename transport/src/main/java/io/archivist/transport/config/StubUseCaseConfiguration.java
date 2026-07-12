package io.archivist.transport.config;

import io.archivist.application.usecase.FindConceptsUseCase;
import io.archivist.application.usecase.FindDebriefsUseCase;
import io.archivist.application.usecase.FindDecisionsUseCase;
import io.archivist.application.usecase.FindPeopleUseCase;
import io.archivist.application.usecase.FindProjectsUseCase;
import io.archivist.application.usecase.FindReadingsUseCase;
import io.archivist.application.usecase.FindRelatedKnowledgeUseCase;
import io.archivist.application.usecase.RetrieveContextUseCase;
import io.archivist.domain.port.in.FindConcepts;
import io.archivist.domain.port.in.FindDebriefs;
import io.archivist.domain.port.in.FindDecisions;
import io.archivist.domain.port.in.FindPeople;
import io.archivist.domain.port.in.FindProjects;
import io.archivist.domain.port.in.FindReadings;
import io.archivist.domain.port.in.FindRelatedKnowledge;
import io.archivist.domain.port.in.RetrieveContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StubUseCaseConfiguration {

    @Bean
    public RetrieveContext retrieveContext() {
        return new RetrieveContextUseCase();
    }

    @Bean
    public FindDecisions findDecisions() {
        return new FindDecisionsUseCase();
    }

    @Bean
    public FindProjects findProjects() {
        return new FindProjectsUseCase();
    }

    @Bean
    public FindPeople findPeople() {
        return new FindPeopleUseCase();
    }

    @Bean
    public FindConcepts findConcepts() {
        return new FindConceptsUseCase();
    }

    @Bean
    public FindRelatedKnowledge findRelatedKnowledge() {
        return new FindRelatedKnowledgeUseCase();
    }

    @Bean
    public FindReadings findReadings() {
        return new FindReadingsUseCase();
    }

    @Bean
    public FindDebriefs findDebriefs() {
        return new FindDebriefsUseCase();
    }
}
