package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.port.out.KnowledgeCorpus;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecondBrainCorpusProperties.class, SecondBrainMappingProperties.class})
public class SecondBrainConfiguration {

    @Bean
    KnowledgeCorpus knowledgeCorpus(
            SecondBrainCorpusProperties corpusProperties, SecondBrainMappingProperties mappingProperties) {
        Path corpusRoot = Path.of(corpusProperties.getPath());
        MetadataEnvelopeParser metadataEnvelopeParser = new MetadataEnvelopeParser();
        SourceIdNormalizer sourceIdNormalizer = new SourceIdNormalizer();
        ZoneTypeResolver zoneTypeResolver = new ZoneTypeResolver(
                mappingProperties.getZonePrefixes(), mappingProperties.getZoneDefaultTypes());
        CorpusEntryMapper corpusEntryMapper = new CorpusEntryMapper(sourceIdNormalizer, zoneTypeResolver);
        CorpusWalker corpusWalker = new CorpusWalker(corpusProperties.getIgnoreGlobs());
        CorpusEntryLoader corpusEntryLoader = new CorpusEntryLoader(
                corpusRoot,
                corpusProperties.getMaxEntryBytes(),
                corpusProperties.getMetadataReadBytes(),
                metadataEnvelopeParser,
                corpusEntryMapper);
        return new SecondBrainKnowledgeCorpus(
                corpusRoot,
                corpusProperties.getLoadConcurrency(),
                corpusWalker,
                corpusEntryLoader);
    }
}
