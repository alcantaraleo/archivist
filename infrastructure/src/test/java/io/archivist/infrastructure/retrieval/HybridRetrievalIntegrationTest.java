package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.domain.port.out.KnowledgeGateway;
import io.archivist.infrastructure.retrieval.embedding.EmbeddingIndexCache;
import io.archivist.infrastructure.retrieval.embedding.EmbeddingProperties;
import io.archivist.infrastructure.retrieval.embedding.EmbeddingRetrievalStrategy;
import io.archivist.infrastructure.retrieval.embedding.EntryChunker;
import io.archivist.infrastructure.retrieval.embedding.InMemoryVectorStore;
import io.archivist.infrastructure.retrieval.embedding.StubTextEmbedder;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RetrievalTestConfiguration.class)
class HybridRetrievalIntegrationTest {

    private static final Path SCENARIO_PATH =
            Path.of("specs/007-hybrid-retrieval/contracts/fixture-hybrid-consensus-scenario.json");

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeGateway knowledgeGateway;

    @Autowired
    private KnowledgeCorpus knowledgeCorpus;

    @Autowired
    private RetrievalStrategyRegistry registry;

    @Autowired
    private List<RetrievalStrategy> strategies;

    @BeforeAll
    static void copyIsolatedHybridFixtures() throws Exception {
        JsonNode scenario = readScenario();
        for (JsonNode pathNode : scenario.get("testCorpus").get("copyFromClasspath")) {
            String resourcePath = pathNode.stringValue();
            String relative = resourcePath.substring("fixture-corpus/".length());
            Path destination = corpusRoot.resolve(relative);
            copyResource(resourcePath, destination);
        }
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "hybrid");
        registry.add("archivist.retrieval.embedding.embedder", () -> "stub");
        registry.add("archivist.retrieval.embedding.store", () -> "memory");
        registry.add("archivist.retrieval.embedding.dimensions", () -> "32");
        registry.add("archivist.retrieval.max-results", () -> "20");
        registry.add("archivist.retrieval.hybrid.max-results", () -> "20");
    }

    @Test
    void hybridIsRegisteredAndActive() {
        assertTrue(strategies.stream().anyMatch(strategy -> "hybrid".equals(strategy.name())));
        assertInstanceOf(HybridRetrievalStrategy.class, registry.getActive());
        assertEquals("hybrid", registry.getActive().name());
    }

    @Test
    void blankQueryReturnsEmpty() {
        assertTrue(knowledgeGateway.retrieve(Query.unrestricted("   ", 10)).isEmpty());
    }

    @Test
    @Timeout(10)
    void consensusScenarioOrdersByScoreThreeThenTwoThenOne() throws Exception {
        JsonNode scenario = readScenario();
        int dimensions = scenario.get("embeddingDimensions").intValue();
        String queryText = scenario.get("query").get("text").stringValue();
        int maxResults = scenario.get("query").get("maxResults").intValue();

        Map<String, float[]> exactOverrides = new HashMap<>();
        scenario.get("stubVectors").get("exactOverrides").properties().forEach(entry -> {
            exactOverrides.put(entry.getKey(), parseUnit(entry.getValue().stringValue(), dimensions));
        });
        Map<String, float[]> containsOverrides = new HashMap<>();
        scenario.get("stubVectors").get("containsOverrides").properties().forEach(entry -> {
            containsOverrides.put(entry.getKey(), parseUnit(entry.getValue().stringValue(), dimensions));
        });

        StubTextEmbedder embedder = new StubTextEmbedder(dimensions, exactOverrides, containsOverrides);
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDimensions(dimensions);
        embeddingProperties.setEmbedder("stub");
        embeddingProperties.setStore("memory");
        embeddingProperties.setTopK(50);

        RetrievalStrategy lexical = new LexicalRetrievalStrategy(knowledgeCorpus);
        RetrievalStrategy bm25 = new Bm25RetrievalStrategy(
                knowledgeCorpus, new Bm25IndexCache(new Bm25IndexBuilder(), new Bm25Properties()), new Bm25Properties());
        RetrievalStrategy embedding = new EmbeddingRetrievalStrategy(
                knowledgeCorpus,
                embedder,
                new EmbeddingIndexCache(
                        embedder, new InMemoryVectorStore(), new EntryChunker(embeddingProperties), embeddingProperties),
                embeddingProperties);

        HybridRetrievalStrategy hybrid =
                new HybridRetrievalStrategy(lexical, bm25, embedding, new HybridProperties());

        List<Evidence> results = hybrid.retrieve(Query.unrestricted(queryText, maxResults));
        assertProvenanceComplete(results);

        JsonNode expectedOrder = scenario.get("expected").get("orderingByConsensusScore");
        assertEquals(expectedOrder.size(), results.size());
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertEquals(
                    expectedOrder.get(i).stringValue(),
                    results.get(i).provenance().sourceId(),
                    "position " + i);
        }
        assertEquals(
                scenario.get("expected").get("hybridTopSourceId").stringValue(),
                results.getFirst().provenance().sourceId());
    }

    private static float[] parseUnit(String token, int dimensions) {
        // unit(n) → axis-n unit vector
        if (!token.startsWith("unit(") || !token.endsWith(")")) {
            throw new IllegalArgumentException("unsupported stub vector token: " + token);
        }
        int axis = Integer.parseInt(token.substring("unit(".length(), token.length() - 1));
        float[] vector = new float[dimensions];
        vector[axis] = 1.0f;
        return vector;
    }

    private static JsonNode readScenario() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        return jsonMapper.readTree(SCENARIO_PATH.toFile());
    }

    private static void copyResource(String resourcePath, Path destination) throws Exception {
        URL resourceUrl = HybridRetrievalIntegrationTest.class.getClassLoader().getResource(resourcePath);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of(resourceUrl.toURI()), destination);
    }

    private static void assertProvenanceComplete(List<Evidence> results) {
        assertFalse(results.isEmpty());
        for (Evidence evidence : results) {
            Provenance provenance = evidence.provenance();
            assertNotNull(provenance);
            assertFalse(provenance.sourceId().isBlank());
            assertNotNull(provenance.title());
            assertNotNull(provenance.type());
            assertNotNull(provenance.zone());
            assertNotNull(provenance.tags());
            assertNotNull(provenance.sources());
            assertNotNull(provenance.created());
            assertNotNull(provenance.updated());
            assertNotNull(provenance.contentAvailability());
        }
    }
}
