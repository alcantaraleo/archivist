package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class CorpusFingerprint {

    String compute(KnowledgeCorpus corpus) {
        Objects.requireNonNull(corpus, "corpus");
        List<Provenance> catalog = corpus.catalog();
        List<Provenance> sorted = catalog.stream()
                .sorted(Comparator.comparing(Provenance::sourceId))
                .toList();

        StringBuilder lines = new StringBuilder();
        for (Provenance provenance : sorted) {
            lines.append(provenance.sourceId())
                    .append('|')
                    .append(provenance.updated().toEpochMilli())
                    .append('|')
                    .append(provenance.contentAvailability().name())
                    .append('\n');
        }

        byte[] digest = sha256(lines.toString());
        return sorted.size() + ":" + HexFormat.of().formatHex(digest);
    }

    private static byte[] sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
