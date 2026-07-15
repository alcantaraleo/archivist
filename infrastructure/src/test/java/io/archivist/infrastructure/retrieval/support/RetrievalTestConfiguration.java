package io.archivist.infrastructure.retrieval.support;

import io.archivist.infrastructure.retrieval.ArchivistRetrievalAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import(ArchivistRetrievalAutoConfiguration.class)
public class RetrievalTestConfiguration {
}
