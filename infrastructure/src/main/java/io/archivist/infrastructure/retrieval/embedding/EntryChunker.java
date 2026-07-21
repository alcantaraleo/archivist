package io.archivist.infrastructure.retrieval.embedding;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EntryChunker {

    private final int chunkSizeChars;
    private final int chunkOverlapChars;

    public EntryChunker(EmbeddingProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.chunkSizeChars = properties.getChunkSizeChars();
        this.chunkOverlapChars = properties.getChunkOverlapChars();
    }

    public EntryChunker(int chunkSizeChars, int chunkOverlapChars) {
        if (chunkSizeChars < 100) {
            throw new IllegalArgumentException("chunkSizeChars must be >= 100");
        }
        if (chunkOverlapChars < 0 || chunkOverlapChars >= chunkSizeChars) {
            throw new IllegalArgumentException("chunkOverlapChars must be >= 0 and < chunkSizeChars");
        }
        this.chunkSizeChars = chunkSizeChars;
        this.chunkOverlapChars = chunkOverlapChars;
    }

    List<ChunkText> chunk(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        Provenance provenance = evidence.provenance();
        String prefix = buildPrefix(provenance);
        String body = provenance.contentAvailability() == ContentAvailability.AVAILABLE
                ? nullToEmpty(evidence.content())
                : "";
        List<String> bodySlices = splitBody(body);
        List<ChunkText> chunks = new ArrayList<>(bodySlices.size());
        for (int i = 0; i < bodySlices.size(); i++) {
            String slice = bodySlices.get(i);
            String text = slice.isEmpty() ? prefix.stripTrailing() : prefix + slice;
            chunks.add(new ChunkText(i, text));
        }
        return List.copyOf(chunks);
    }

    private static String buildPrefix(Provenance provenance) {
        String tags = provenance.tags() == null || provenance.tags().isEmpty()
                ? ""
                : provenance.tags().stream().collect(Collectors.joining(" "));
        return "Title: " + nullToEmpty(provenance.title()) + "\nTags: " + tags + "\n\n";
    }

    private List<String> splitBody(String body) {
        if (body.isEmpty()) {
            return List.of("");
        }
        List<String> paragraphs = splitParagraphs(body);
        List<String> softChunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > chunkSizeChars) {
                if (!current.isEmpty()) {
                    softChunks.add(current.toString());
                    current.setLength(0);
                }
                softChunks.addAll(hardWrap(paragraph));
                continue;
            }
            if (!current.isEmpty() && current.length() + 2 + paragraph.length() > chunkSizeChars) {
                softChunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (!current.isEmpty()) {
            softChunks.add(current.toString());
        }
        if (softChunks.isEmpty()) {
            return List.of("");
        }
        return softChunks;
    }

    private static List<String> splitParagraphs(String body) {
        String[] parts = body.split("\n\n+", -1);
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                paragraphs.add(part.strip());
            }
        }
        return paragraphs;
    }

    private List<String> hardWrap(String text) {
        List<String> slices = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSizeChars, text.length());
            slices.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - chunkOverlapChars, start + 1);
        }
        return slices;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record ChunkText(int chunkIndex, String text) {}
}
