package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.retrieval.LexicalRetrievalStrategy;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RetrievalTestConfiguration.class)
class EmbeddingRankingDifferentiationTest {

    private static final int DIMS = 32;

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeCorpus knowledgeCorpus;

    @BeforeAll
    static void copyRankingFixturesOnly() throws Exception {
        Path conceptsDir = corpusRoot.resolve("wiki/concepts");
        Files.createDirectories(conceptsDir);
        copyResource(
                "fixture-corpus/wiki/concepts/ranking-embedding-semantic-target.md",
                conceptsDir.resolve("ranking-embedding-semantic-target.md"));
        copyResource(
                "fixture-corpus/wiki/concepts/ranking-embedding-term-bait.md",
                conceptsDir.resolve("ranking-embedding-term-bait.md"));
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "lexical");
        registry.add("archivist.retrieval.embedding.embedder", () -> "stub");
        registry.add("archivist.retrieval.embedding.dimensions", () -> String.valueOf(DIMS));
    }

    @Test
    void embeddingOrderingDiffersFromLexicalForParaphraseFixture() throws Exception {
        JsonNode scenario = readRankingScenario();
        String queryText = scenario.get("query").get("text").stringValue();
        int maxResults = scenario.get("query").get("maxResults").intValue();
        Query query = Query.unrestricted(queryText, maxResults);

        float[] queryVector = unit(0);
        float[] nearVector = unit(0);
        float[] farVector = unit(1);

        StubTextEmbedder embedder = new StubTextEmbedder(
                DIMS,
                Map.of(queryText, queryVector),
                Map.of(
                        "ranking_semantic_marker", nearVector,
                        "ranking_term_bait_marker", farVector));

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(DIMS);
        properties.setEmbedder("stub");
        properties.setStore("memory");
        properties.setTopK(50);

        EmbeddingRetrievalStrategy embedding = new EmbeddingRetrievalStrategy(
                knowledgeCorpus,
                embedder,
                new EmbeddingIndexCache(embedder, new InMemoryVectorStore(), new EntryChunker(properties), properties),
                properties);

        List<Evidence> embeddingResults = embedding.retrieve(query);
        List<Evidence> lexicalResults = new LexicalRetrievalStrategy(knowledgeCorpus).retrieve(query);

        JsonNode expected = scenario.get("expected");
        assertEquals(
                expected.get("embeddingTopSourceId").stringValue(),
                embeddingResults.getFirst().provenance().sourceId());
        assertEquals(
                expected.get("lexicalTopSourceId").stringValue(),
                lexicalResults.getFirst().provenance().sourceId());
        assertNotEquals(
                embeddingResults.getFirst().provenance().sourceId(),
                lexicalResults.getFirst().provenance().sourceId());
    }

    private static float[] unit(int axis) {
        float[] vector = new float[DIMS];
        vector[axis] = 1.0f;
        return vector;
    }

    private static JsonNode readRankingScenario() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        Path fixturePath =
                Path.of("specs/006-embedding-retrieval/contracts/fixture-embedding-ranking-scenario.json");
        return jsonMapper.readTree(fixturePath.toFile());
    }

    private static void copyResource(String resourcePath, Path destination) throws Exception {
        URL resourceUrl = EmbeddingRankingDifferentiationTest.class.getClassLoader().getResource(resourcePath);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of(resourceUrl.toURI()), destination);
    }
}
