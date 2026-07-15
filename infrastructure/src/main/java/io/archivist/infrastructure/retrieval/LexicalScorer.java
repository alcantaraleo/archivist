package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class LexicalScorer {

    private static final int TITLE_WEIGHT = 3;
    private static final int TAG_WEIGHT = 2;
    private static final int BODY_WEIGHT = 1;

    List<String> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    int score(Evidence evidence, List<String> queryTokens) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(queryTokens, "queryTokens");
        if (queryTokens.isEmpty()) {
            return 0;
        }

        Provenance provenance = evidence.provenance();
        String title = provenance.title() == null ? "" : provenance.title().toLowerCase(Locale.ROOT);
        List<String> tags = provenance.tags() == null ? List.of() : provenance.tags();
        boolean searchBody = provenance.contentAvailability() == ContentAvailability.AVAILABLE;
        String body = searchBody && evidence.content() != null
                ? evidence.content().toLowerCase(Locale.ROOT)
                : "";

        int total = 0;
        for (String token : queryTokens) {
            if (title.contains(token)) {
                total += TITLE_WEIGHT;
            }
            for (String tag : tags) {
                if (tag != null && tag.toLowerCase(Locale.ROOT).contains(token)) {
                    total += TAG_WEIGHT;
                }
            }
            if (searchBody && body.contains(token)) {
                total += BODY_WEIGHT;
            }
        }
        return total;
    }

    List<Evidence> rank(List<Evidence> candidates, List<String> queryTokens) {
        List<ScoredEvidence> scored = new ArrayList<>();
        for (Evidence evidence : candidates) {
            int evidenceScore = score(evidence, queryTokens);
            if (evidenceScore > 0) {
                scored.add(new ScoredEvidence(evidence, evidenceScore));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredEvidence::score)
                .reversed()
                .thenComparing(item -> item.evidence().provenance().sourceId()));
        return scored.stream().map(ScoredEvidence::evidence).toList();
    }

    private record ScoredEvidence(Evidence evidence, int score) {}
}
