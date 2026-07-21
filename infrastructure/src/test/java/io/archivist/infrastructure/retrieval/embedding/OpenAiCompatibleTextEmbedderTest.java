package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleTextEmbedderTest {

    @Test
    void requireConfiguredFailsWithoutBaseUrl() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(3);
        OpenAiCompatibleTextEmbedder embedder = new OpenAiCompatibleTextEmbedder(properties);
        assertThrows(IllegalStateException.class, embedder::requireConfigured);
        assertThrows(IllegalStateException.class, () -> embedder.embed("hi"));
    }

    @Test
    void nameAndDimensions() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(8);
        properties.getOpenai().setBaseUrl("http://localhost:9");
        OpenAiCompatibleTextEmbedder embedder = new OpenAiCompatibleTextEmbedder(properties);
        assertEquals("openai-compatible", embedder.name());
        assertEquals(8, embedder.dimensions());
    }

    @Test
    void embedBatchAlignsVectorsByResponseIndex() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        """
                        {"data":[
                          {"index":1,"embedding":[0.0,1.0,0.0]},
                          {"index":0,"embedding":[1.0,0.0,0.0]}
                        ]}\
                        """);
        when(httpClient.<String>send(any(), any())).thenReturn(response);

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimensions(3);
        properties.getOpenai().setBaseUrl("http://localhost:9");
        OpenAiCompatibleTextEmbedder embedder = new OpenAiCompatibleTextEmbedder(properties, httpClient);

        List<float[]> vectors = embedder.embedBatch(List.of("first", "second"));
        assertEquals(2, vectors.size());
        assertArrayEquals(new float[] {1.0f, 0.0f, 0.0f}, vectors.get(0), 1e-5f);
        assertArrayEquals(new float[] {0.0f, 1.0f, 0.0f}, vectors.get(1), 1e-5f);
    }
}
