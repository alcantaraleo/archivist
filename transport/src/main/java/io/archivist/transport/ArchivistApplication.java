package io.archivist.transport;

import io.archivist.transport.config.ArchivistProperties;
import io.archivist.transport.config.StubUseCaseConfiguration;
import io.archivist.transport.config.TransportJacksonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties(ArchivistProperties.class)
@Import({StubUseCaseConfiguration.class, TransportJacksonConfiguration.class})
public class ArchivistApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchivistApplication.class, args);
    }
}
