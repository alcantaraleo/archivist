package io.archivist.transport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class TransportJacksonConfiguration {

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
