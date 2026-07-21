package io.archivist.infrastructure.retrieval.embedding;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * Process-local ONNX embedder (Spring AI Transformers). Lazy-initialises the model on first use.
 */
public final class LocalTextEmbedder implements TextEmbedder {

    static final String NAME = "local";

    private final EmbeddingProperties.Local localProperties;
    private final int dimensions;
    private final Object lock = new Object();
    private volatile TransformersEmbeddingModel model;

    LocalTextEmbedder(EmbeddingProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.localProperties = properties.getLocal();
        this.dimensions = properties.getDimensions();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        return VectorMath.l2Normalize(model().embed(text == null ? "" : text));
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> raw = model().embed(texts);
        return raw.stream().map(VectorMath::l2Normalize).toList();
    }

    private TransformersEmbeddingModel model() {
        TransformersEmbeddingModel existing = model;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (model == null) {
                model = createModel();
            }
            return model;
        }
    }

    private TransformersEmbeddingModel createModel() {
        try {
            TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
            if (localProperties.getCacheDirectory() != null && !localProperties.getCacheDirectory().isBlank()) {
                embeddingModel.setResourceCacheDirectory(localProperties.getCacheDirectory());
            }
            if (localProperties.getModelResource() != null && !localProperties.getModelResource().isBlank()) {
                embeddingModel.setModelResource(localProperties.getModelResource());
            }
            if (localProperties.getTokenizerResource() != null
                    && !localProperties.getTokenizerResource().isBlank()) {
                embeddingModel.setTokenizerResource(localProperties.getTokenizerResource());
            }
            embeddingModel.afterPropertiesSet();
            return embeddingModel;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to initialise local TransformersEmbeddingModel (check model resources / cache)",
                    exception);
        }
    }
}
