package io.archivist.infrastructure.retrieval.embedding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class EmbeddingProperties {

    @NotBlank
    private String store = "memory";

    @NotBlank
    private String embedder = "local";

    @Min(1)
    private int dimensions = 384;

    @Min(1)
    private int topK = 50;

    private Float minScore;

    @NotNull
    private Duration indexTtl = Duration.ofMinutes(15);

    @Min(100)
    private int chunkSizeChars = 1200;

    @Min(0)
    private int chunkOverlapChars = 150;

    @Valid
    @NestedConfigurationProperty
    private Local local = new Local();

    @Valid
    @NestedConfigurationProperty
    private Openai openai = new Openai();

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getEmbedder() {
        return embedder;
    }

    public void setEmbedder(String embedder) {
        this.embedder = embedder;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Float getMinScore() {
        return minScore;
    }

    public void setMinScore(Float minScore) {
        this.minScore = minScore;
    }

    public Duration getIndexTtl() {
        return indexTtl;
    }

    public void setIndexTtl(Duration indexTtl) {
        this.indexTtl = indexTtl;
    }

    public int getChunkSizeChars() {
        return chunkSizeChars;
    }

    public void setChunkSizeChars(int chunkSizeChars) {
        this.chunkSizeChars = chunkSizeChars;
    }

    public int getChunkOverlapChars() {
        return chunkOverlapChars;
    }

    public void setChunkOverlapChars(int chunkOverlapChars) {
        this.chunkOverlapChars = chunkOverlapChars;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Openai getOpenai() {
        return openai;
    }

    public void setOpenai(Openai openai) {
        this.openai = openai;
    }

    @AssertTrue(message = "archivist.retrieval.embedding.index-ttl must not be negative")
    public boolean isIndexTtlNonNegative() {
        return indexTtl != null && !indexTtl.isNegative();
    }

    @AssertTrue(message = "archivist.retrieval.embedding.chunk-overlap-chars must be < chunk-size-chars")
    public boolean isChunkOverlapLessThanSize() {
        return chunkOverlapChars < chunkSizeChars;
    }

    @AssertTrue(message = "archivist.retrieval.embedding.min-score must be finite when set")
    public boolean isMinScoreFiniteWhenSet() {
        return minScore == null || Float.isFinite(minScore);
    }

    static class Local {

        private String modelResource = "";

        private String tokenizerResource = "";

        private String cacheDirectory = System.getProperty("java.io.tmpdir") + "/archivist-onnx-model";

        public String getModelResource() {
            return modelResource;
        }

        public void setModelResource(String modelResource) {
            this.modelResource = modelResource;
        }

        public String getTokenizerResource() {
            return tokenizerResource;
        }

        public void setTokenizerResource(String tokenizerResource) {
            this.tokenizerResource = tokenizerResource;
        }

        public String getCacheDirectory() {
            return cacheDirectory;
        }

        public void setCacheDirectory(String cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
        }
    }

    static class Openai {

        private String baseUrl = "";

        private String apiKey = "";

        private String model = "text-embedding-3-small";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
