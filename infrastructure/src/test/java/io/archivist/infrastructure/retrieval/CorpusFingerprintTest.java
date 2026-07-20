package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.KnowledgeType;
import io.archivist.domain.model.KnowledgeZone;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusFingerprintTest {

    private final CorpusFingerprint fingerprint = new CorpusFingerprint();

    @Test
    void sameCatalogProducesSameFingerprint() {
        KnowledgeCorpus corpus = stubCatalog(entry("wiki/a", "2026-01-01T00:00:00Z", ContentAvailability.AVAILABLE));

        assertEquals(fingerprint.compute(corpus), fingerprint.compute(corpus));
    }

    @Test
    void changedUpdatedChangesFingerprint() {
        KnowledgeCorpus before = stubCatalog(entry("wiki/a", "2026-01-01T00:00:00Z", ContentAvailability.AVAILABLE));
        KnowledgeCorpus after = stubCatalog(entry("wiki/a", "2026-01-02T00:00:00Z", ContentAvailability.AVAILABLE));

        assertNotEquals(fingerprint.compute(before), fingerprint.compute(after));
    }

    @Test
    void changedEntryCountChangesFingerprint() {
        KnowledgeCorpus one = stubCatalog(entry("wiki/a", "2026-01-01T00:00:00Z", ContentAvailability.AVAILABLE));
        KnowledgeCorpus two = stubCatalog(
                entry("wiki/a", "2026-01-01T00:00:00Z", ContentAvailability.AVAILABLE),
                entry("wiki/b", "2026-01-01T00:00:00Z", ContentAvailability.AVAILABLE));

        assertNotEquals(fingerprint.compute(one), fingerprint.compute(two));
    }

    private static KnowledgeCorpus stubCatalog(Provenance... provenances) {
        List<Provenance> catalog = List.of(provenances);
        return new KnowledgeCorpus() {
            @Override
            public List<Provenance> catalog() {
                return catalog;
            }

            @Override
            public java.util.Optional<io.archivist.domain.model.Evidence> loadBySourceId(String sourceId) {
                return java.util.Optional.empty();
            }

            @Override
            public List<io.archivist.domain.model.Evidence> loadAll() {
                return List.of();
            }
        };
    }

    private static Provenance entry(String sourceId, String updated, ContentAvailability availability) {
        return new Provenance(
                sourceId,
                "title",
                KnowledgeType.CONCEPT,
                KnowledgeZone.SYNTHESIZED,
                List.of(),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse(updated),
                availability);
    }
}
