package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import java.util.List;

/**
 * Infrastructure-internal retrieval SPI. Not a domain port — MUST NOT be referenced from
 * {@code domain}, {@code application}, or {@code transport} sources.
 *
 * <p>Contract: {@code specs/004-lexical-retrieval/contracts/retrieval-strategy-spi.md}
 */
public interface RetrievalStrategy {

    /**
     * Stable configuration key — must match {@code archivist.retrieval.active-strategy}.
     */
    String name();

    /**
     * Execute retrieval for the given domain query.
     * Returns ranked, deduplicated evidence (may be empty).
     */
    List<Evidence> retrieve(Query query);
}
