package io.archivist.domain;

import io.archivist.domain.model.KnowledgeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainModuleIsolationTest {

    @Test
    void knowledgeTypeContainsExpectedConstants() {
        assertEquals(8, KnowledgeType.values().length);
        assertEquals(KnowledgeType.CONCEPT, KnowledgeType.valueOf("CONCEPT"));
    }
}
