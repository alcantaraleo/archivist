package io.archivist.transport.mcp;

import io.archivist.domain.model.Evidence;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EvidenceJsonMapper {

    private final JsonMapper jsonMapper;

    public EvidenceJsonMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String toJson(List<Evidence> evidence) {
        try {
            return jsonMapper.writeValueAsString(evidence);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise evidence list", exception);
        }
    }
}
