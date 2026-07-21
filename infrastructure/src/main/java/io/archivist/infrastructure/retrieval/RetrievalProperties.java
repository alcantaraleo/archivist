package io.archivist.infrastructure.retrieval;

import io.archivist.infrastructure.retrieval.embedding.EmbeddingProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "archivist.retrieval")
public class RetrievalProperties {

    @NotBlank
    private String activeStrategy = "lexical";

    @Min(1)
    private int maxResults = 20;

    @Valid
    @NestedConfigurationProperty
    private Bm25Properties bm25 = new Bm25Properties();

    @Valid
    @NestedConfigurationProperty
    private EmbeddingProperties embedding = new EmbeddingProperties();

    public String getActiveStrategy() {
        return activeStrategy;
    }

    public void setActiveStrategy(String activeStrategy) {
        this.activeStrategy = activeStrategy;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public Bm25Properties getBm25() {
        return bm25;
    }

    public void setBm25(Bm25Properties bm25) {
        this.bm25 = bm25;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }
}
