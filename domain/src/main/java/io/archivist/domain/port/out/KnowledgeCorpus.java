package io.archivist.domain.port.out;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import java.util.List;
import java.util.Optional;

public interface KnowledgeCorpus {

    /**
     * Returns provenance metadata for every discoverable entry in the corpus,
     * including entries whose body was not loaded due to size limits.
     * Must not throw when individual entries fail to parse; unrecoverable entries are omitted.
     * Order is unspecified.
     */
    List<Provenance> catalog();

    /**
     * Loads a single entry by stable sourceId. Returns empty when not found.
     */
    Optional<Evidence> loadBySourceId(String sourceId);

    /**
     * Loads all discoverable entries with provenance; body present only when
     * contentAvailability is AVAILABLE. Size-limited entries included with empty content.
     * Entries that fail parsing are omitted. Order is unspecified.
     */
    List<Evidence> loadAll();
}
