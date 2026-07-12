package io.archivist.transport;

import io.archivist.transport.config.ArchivistProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ArchivistProperties.class)
public class ArchivistApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchivistApplication.class, args);
    }
}
