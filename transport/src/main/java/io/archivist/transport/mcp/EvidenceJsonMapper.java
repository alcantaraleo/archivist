package io.archivist.transport.mcp;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
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
            List<EvidenceResponse> responses = evidence.stream().map(this::toResponse).toList();
            return jsonMapper.writeValueAsString(responses);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise evidence list", exception);
        }
    }

    private EvidenceResponse toResponse(Evidence evidence) {
        return new EvidenceResponse(evidence.content(), toProvenanceResponse(evidence.provenance()));
    }

    private ProvenanceResponse toProvenanceResponse(Provenance provenance) {
        return new ProvenanceResponse(
                provenance.sourceId(),
                provenance.title(),
                provenance.type(),
                provenance.zone(),
                provenance.tags(),
                provenance.sources(),
                provenance.created(),
                provenance.updated());
    }
}
