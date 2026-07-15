package io.archivist.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryTest {

    @Test
    void unrestrictedHasEmptyFilters() {
        Query query = Query.unrestricted("topic", 20);

        assertEquals("topic", query.text());
        assertTrue(query.types().isEmpty());
        assertTrue(query.zones().isEmpty());
        assertEquals(20, query.maxResults());
    }

    @Test
    void withTypeSetsSingleTypeFilter() {
        Query query = Query.withType("adr", KnowledgeType.DECISION, 10);

        assertEquals(Set.of(KnowledgeType.DECISION), query.types());
        assertTrue(query.zones().isEmpty());
        assertEquals(10, query.maxResults());
    }

    @Test
    void withTypesSetsMultiTypeFilter() {
        Query query = Query.withTypes("topic", Set.of(KnowledgeType.CONCEPT, KnowledgeType.SYNTHESIS), 5);

        assertEquals(Set.of(KnowledgeType.CONCEPT, KnowledgeType.SYNTHESIS), query.types());
        assertTrue(query.zones().isEmpty());
    }

    @Test
    void rejectsNonPositiveMaxResults() {
        assertThrows(IllegalArgumentException.class, () -> Query.unrestricted("text", 0));
        assertThrows(IllegalArgumentException.class, () -> Query.unrestricted("text", -1));
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new Query(null, Set.of(), Set.of(), 1));
        assertThrows(NullPointerException.class, () -> new Query("text", null, Set.of(), 1));
        assertThrows(NullPointerException.class, () -> new Query("text", Set.of(), null, 1));
        assertThrows(NullPointerException.class, () -> Query.withType("text", null, 1));
        assertThrows(NullPointerException.class, () -> Query.withTypes("text", null, 1));
    }

    @Test
    void copiesTypeSetDefensively() {
        Set<KnowledgeType> mutable = new java.util.HashSet<>(Set.of(KnowledgeType.PERSON));
        Query query = Query.withTypes("name", mutable, 3);
        mutable.add(KnowledgeType.PROJECT);

        assertEquals(Set.of(KnowledgeType.PERSON), query.types());
    }
}
