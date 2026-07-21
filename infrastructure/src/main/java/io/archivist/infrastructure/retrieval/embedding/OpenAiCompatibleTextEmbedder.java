package io.archivist.infrastructure.retrieval.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI-compatible HTTP embeddings adapter. Credentials are never logged.
 */
public final class OpenAiCompatibleTextEmbedder implements TextEmbedder {

    static final String NAME = "openai-compatible";

    private final EmbeddingProperties.Openai openai;
    private final int dimensions;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;

    public OpenAiCompatibleTextEmbedder(EmbeddingProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public OpenAiCompatibleTextEmbedder(EmbeddingProperties properties, HttpClient httpClient) {
        Objects.requireNonNull(properties, "properties");
        this.openai = properties.getOpenai();
        this.dimensions = properties.getDimensions();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.jsonMapper = JsonMapper.builder().findAndAddModules().build();
    }

    public void requireConfigured() {
        if (openai.getBaseUrl() == null || openai.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "archivist.retrieval.embedding.openai.base-url is required when embedder=openai-compatible");
        }
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
        return embedBatch(List.of(text == null ? "" : text)).getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        requireConfigured();
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            ObjectNode body = jsonMapper.createObjectNode();
            body.put("model", openai.getModel());
            ArrayNode input = body.putArray("input");
            for (String text : texts) {
                input.add(text == null ? "" : text);
            }
            String payload = jsonMapper.writeValueAsString(body);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(openai.getBaseUrl()) + "/v1/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (openai.getApiKey() != null && !openai.getApiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + openai.getApiKey());
            }
            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenAI-compatible embeddings HTTP " + response.statusCode());
            }
            return parseEmbeddings(response.body(), texts.size());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("OpenAI-compatible embeddings request failed", exception);
        }
    }

    private List<float[]> parseEmbeddings(String responseBody, int expectedCount) throws IOException {
        JsonNode root = jsonMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.size() != expectedCount) {
            throw new IllegalStateException("Unexpected embeddings response shape");
        }
        List<IndexedVector> indexed = new ArrayList<>(expectedCount);
        for (int position = 0; position < data.size(); position++) {
            JsonNode item = data.get(position);
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new IllegalStateException("Missing embedding array in response");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            if (vector.length != dimensions) {
                throw new IllegalStateException(
                        "Embedding dimensions " + vector.length + " != configured " + dimensions);
            }
            JsonNode indexNode = item.get("index");
            int index = indexNode != null && indexNode.isIntegralNumber() ? indexNode.asInt() : position;
            indexed.add(new IndexedVector(index, VectorMath.l2Normalize(vector)));
        }
        indexed.sort(Comparator.comparingInt(IndexedVector::index));
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (IndexedVector entry : indexed) {
            vectors.add(entry.vector());
        }
        return List.copyOf(vectors);
    }

    private record IndexedVector(int index, float[] vector) {}

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }
}
