package io.archivist.transport.support;

import io.archivist.transport.config.StubUseCaseConfiguration;
import io.archivist.transport.config.TransportJacksonConfiguration;
import io.archivist.transport.mcp.ArchivistMcpTools;
import io.archivist.transport.mcp.EvidenceJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {McpServerAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class},
        excludeName = "io.archivist.infrastructure.retrieval.ArchivistRetrievalAutoConfiguration")
@Import({
    StubUseCaseConfiguration.class,
    TransportJacksonConfiguration.class,
    ArchivistMcpTools.class,
    EvidenceJsonMapper.class,
    McpServerAnnotationScannerAutoConfiguration.class,
    McpServerSpecificationFactoryAutoConfiguration.class
})
public class McpAdapterTestConfiguration {
}
