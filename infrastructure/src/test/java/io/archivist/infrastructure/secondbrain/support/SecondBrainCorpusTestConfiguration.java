package io.archivist.infrastructure.secondbrain.support;

import io.archivist.infrastructure.secondbrain.SecondBrainConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import(SecondBrainConfiguration.class)
public class SecondBrainCorpusTestConfiguration {
}
