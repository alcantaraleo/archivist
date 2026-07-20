package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Query;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.domain.port.out.KnowledgeGateway;
import io.archivist.infrastructure.retrieval.support.RetrievalTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
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
class Bm25RankingDifferentiationTest {

    @TempDir
    static Path corpusRoot;

    @Autowired
    private KnowledgeGateway knowledgeGateway;

    @Autowired
    private KnowledgeCorpus knowledgeCorpus;

    @BeforeAll
    static void copyRankingFixturesOnly() throws Exception {
        Path conceptsDir = corpusRoot.resolve("wiki/concepts");
        Files.createDirectories(conceptsDir);
        copyResource("fixture-corpus/wiki/concepts/ranking-bm25-sparse.md", conceptsDir.resolve("ranking-bm25-sparse.md"));
        copyResource("fixture-corpus/wiki/concepts/ranking-bm25-dense.md", conceptsDir.resolve("ranking-bm25-dense.md"));
    }

    @DynamicPropertySource
    static void registerCorpusPath(DynamicPropertyRegistry registry) {
        registry.add("archivist.second-brain.path", () -> corpusRoot.toString());
        registry.add("archivist.retrieval.active-strategy", () -> "bm25");
    }

    @Test
    void bm25OrderingDiffersFromLexicalForRankingFixture() throws Exception {
        JsonNode scenario = readRankingScenario();
        Query query = Query.unrestricted(
                scenario.get("query").get("text").stringValue(),
                scenario.get("query").get("maxResults").intValue());

        List<Evidence> bm25Results = knowledgeGateway.retrieve(query);
        LexicalRetrievalStrategy lexical = new LexicalRetrievalStrategy(knowledgeCorpus);
        List<Evidence> lexicalResults = lexical.retrieve(query);

        JsonNode expectations = scenario.get("expectations");
        assertEquals(2, bm25Results.size());
        assertEquals(2, lexicalResults.size());

        assertEquals(
                expectations.get("lexicalTopSourceId").stringValue(),
                lexicalResults.getFirst().provenance().sourceId());
        assertEquals(
                expectations.get("bm25TopSourceId").stringValue(),
                bm25Results.getFirst().provenance().sourceId());

        List<String> lexicalOrder = lexicalResults.stream()
                .map(evidence -> evidence.provenance().sourceId())
                .collect(Collectors.toList());
        List<String> bm25Order = bm25Results.stream()
                .map(evidence -> evidence.provenance().sourceId())
                .collect(Collectors.toList());
        assertFalse(lexicalOrder.equals(bm25Order), "Expected lexical and BM25 order to differ");
    }

    private static JsonNode readRankingScenario() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        Path fixturePath =
                Path.of("specs/005-bm25-retrieval/contracts/fixture-bm25-ranking-scenario.json");
        return jsonMapper.readTree(fixturePath.toFile());
    }

    private static void copyResource(String resourcePath, Path destination) throws Exception {
        URL resourceUrl = Bm25RankingDifferentiationTest.class.getClassLoader().getResource(resourcePath);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of(resourceUrl.toURI()), destination);
    }
}
