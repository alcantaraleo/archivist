package io.archivist.infrastructure.retrieval.embedding;

import java.util.ArrayList;
import java.util.List;

public interface TextEmbedder {

    String name();

    int dimensions();

    float[] embed(String text);

    default List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return List.copyOf(vectors);
    }
}
