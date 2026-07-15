package io.archivist.infrastructure.retrieval;

import io.archivist.infrastructure.secondbrain.SecondBrainConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(RetrievalProperties.class)
@Import({RetrievalConfiguration.class, SecondBrainConfiguration.class})
public class ArchivistRetrievalAutoConfiguration {
}
