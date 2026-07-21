package io.archivist.infrastructure.retrieval.embedding;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic offline embedder for CI. Uses bag-of-words hashing so shared tokens increase
 * cosine similarity. Optional exact and substring overrides support AC-11 fixtures.
 */
public final class StubTextEmbedder implements TextEmbedder {

    static final String NAME = "stub";

    private final int dimensions;
    private final Map<String, float[]> exactOverrides;
    private final Map<String, float[]> containsOverrides;

    public StubTextEmbedder(int dimensions) {
        this(dimensions, Map.of(), Map.of());
    }

    public StubTextEmbedder(int dimensions, Map<String, float[]> exactOverrides) {
        this(dimensions, exactOverrides, Map.of());
    }

    public StubTextEmbedder(
            int dimensions, Map<String, float[]> exactOverrides, Map<String, float[]> containsOverrides) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be >= 1");
        }
        this.dimensions = dimensions;
        this.exactOverrides = copyOverrides(exactOverrides, dimensions);
        this.containsOverrides = copyOverrides(containsOverrides, dimensions);
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
        String key = normalize(text == null ? "" : text);
        float[] exact = exactOverrides.get(key);
        if (exact != null) {
            return VectorMath.copy(exact);
        }
        for (Map.Entry<String, float[]> entry : containsOverrides.entrySet()) {
            if (key.contains(entry.getKey())) {
                return VectorMath.copy(entry.getValue());
            }
        }
        return VectorMath.l2Normalize(bagOfWords(key, dimensions));
    }

    private static Map<String, float[]> copyOverrides(Map<String, float[]> overrides, int dimensions) {
        Map<String, float[]> copy = new HashMap<>();
        for (Map.Entry<String, float[]> entry : overrides.entrySet()) {
            float[] vector = Objects.requireNonNull(entry.getValue(), "override vector");
            if (vector.length != dimensions) {
                throw new IllegalArgumentException(
                        "override vector length " + vector.length + " != dimensions " + dimensions);
            }
            copy.put(normalize(entry.getKey()), VectorMath.l2Normalize(vector));
        }
        return Map.copyOf(copy);
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static float[] bagOfWords(String text, int dimensions) {
        float[] vector = new float[dimensions];
        if (text.isEmpty()) {
            return vector;
        }
        for (String token : text.split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), dimensions);
            vector[index] += 1.0f;
        }
        return vector;
    }
}
