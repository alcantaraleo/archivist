package io.archivist.infrastructure.retrieval.embedding;

public final class VectorMath {

    private VectorMath() {}

    static float[] copy(float[] vector) {
        float[] copy = new float[vector.length];
        System.arraycopy(vector, 0, copy, 0, vector.length);
        return copy;
    }

    static float[] l2Normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += (double) value * value;
        }
        if (sumSquares == 0.0) {
            return copy(vector);
        }
        float norm = (float) Math.sqrt(sumSquares);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    static float cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "vector length mismatch: " + left.length + " vs " + right.length);
        }
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        return (float) dot;
    }
}
