package io.archivist.infrastructure.retrieval;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class RetrievalTokenization {

    private RetrievalTokenization() {}

    static List<String> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
