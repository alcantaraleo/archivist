package io.archivist.infrastructure.retrieval;

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
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.domain.port.out.KnowledgeGateway;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RetrievalConfiguration {

    @Bean
    RetrievalStrategy lexicalRetrievalStrategy(KnowledgeCorpus knowledgeCorpus) {
        return new LexicalRetrievalStrategy(knowledgeCorpus);
    }

    @Bean
    RetrievalStrategyRegistry retrievalStrategyRegistry(
            List<RetrievalStrategy> strategies, RetrievalProperties properties) {
        return new RetrievalStrategyRegistry(strategies, properties.getActiveStrategy());
    }

    @Bean
    KnowledgeGateway knowledgeGateway(RetrievalStrategyRegistry registry) {
        return new KnowledgeGatewayImpl(registry);
    }

    @Bean
    RetrieveContext retrieveContext(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new RetrieveContextUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindDecisions findDecisions(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindDecisionsUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindProjects findProjects(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindProjectsUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindPeople findPeople(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindPeopleUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindConcepts findConcepts(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindConceptsUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindRelatedKnowledge findRelatedKnowledge(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindRelatedKnowledgeUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindReadings findReadings(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindReadingsUseCase(gateway, properties.getMaxResults());
    }

    @Bean
    FindDebriefs findDebriefs(KnowledgeGateway gateway, RetrievalProperties properties) {
        return new FindDebriefsUseCase(gateway, properties.getMaxResults());
    }
}
