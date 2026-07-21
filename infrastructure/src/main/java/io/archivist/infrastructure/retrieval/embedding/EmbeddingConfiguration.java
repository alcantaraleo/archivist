package io.archivist.infrastructure.retrieval.embedding;

import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.infrastructure.retrieval.RetrievalProperties;
import io.archivist.infrastructure.retrieval.RetrievalStrategy;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    TextEmbedder stubTextEmbedder(RetrievalProperties properties) {
        return new StubTextEmbedder(properties.getEmbedding().getDimensions());
    }

    @Bean
    TextEmbedder localTextEmbedder(RetrievalProperties properties) {
        return new LocalTextEmbedder(properties.getEmbedding());
    }

    @Bean
    TextEmbedder openAiCompatibleTextEmbedder(RetrievalProperties properties) {
        return new OpenAiCompatibleTextEmbedder(properties.getEmbedding());
    }

    @Bean
    TextEmbedderRegistry textEmbedderRegistry(List<TextEmbedder> embedders) {
        return new TextEmbedderRegistry(embedders);
    }

    @Bean
    VectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }

    @Bean
    VectorStoreRegistry vectorStoreRegistry(List<VectorStore> stores) {
        return new VectorStoreRegistry(stores);
    }

    @Bean
    EntryChunker entryChunker(RetrievalProperties properties) {
        return new EntryChunker(properties.getEmbedding());
    }

    @Bean
    EmbeddingIndexCache embeddingIndexCache(
            TextEmbedderRegistry textEmbedderRegistry,
            VectorStoreRegistry vectorStoreRegistry,
            EntryChunker entryChunker,
            RetrievalProperties properties) {
        EmbeddingProperties embedding = properties.getEmbedding();
        TextEmbedder activeEmbedder = textEmbedderRegistry.require(embedding.getEmbedder());
        VectorStore activeStore = vectorStoreRegistry.require(embedding.getStore());
        String activeStrategy = properties.getActiveStrategy();
        if (activeEmbedder instanceof OpenAiCompatibleTextEmbedder openAi
                && ("embedding".equals(activeStrategy) || "hybrid".equals(activeStrategy))) {
            openAi.requireConfigured();
        }
        return new EmbeddingIndexCache(activeEmbedder, activeStore, entryChunker, embedding);
    }

    @Bean
    RetrievalStrategy embeddingRetrievalStrategy(
            KnowledgeCorpus knowledgeCorpus,
            TextEmbedderRegistry textEmbedderRegistry,
            EmbeddingIndexCache embeddingIndexCache,
            RetrievalProperties properties) {
        EmbeddingProperties embedding = properties.getEmbedding();
        TextEmbedder activeEmbedder = textEmbedderRegistry.require(embedding.getEmbedder());
        return new EmbeddingRetrievalStrategy(
                knowledgeCorpus, activeEmbedder, embeddingIndexCache, embedding);
    }
}
