package io.archivist.infrastructure.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import io.archivist.infrastructure.retrieval.RetrievalProperties;

class EmbeddingPropertiesTest {

    @Test
    void defaultsMatchContract() {
        EmbeddingProperties properties = new RetrievalProperties().getEmbedding();

        assertEquals("memory", properties.getStore());
        assertEquals("local", properties.getEmbedder());
        assertEquals(384, properties.getDimensions());
        assertEquals(50, properties.getTopK());
        assertEquals(null, properties.getMinScore());
        assertEquals(Duration.ofMinutes(15), properties.getIndexTtl());
        assertEquals(1200, properties.getChunkSizeChars());
        assertEquals(150, properties.getChunkOverlapChars());
        assertEquals("text-embedding-3-small", properties.getOpenai().getModel());
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of("archivist.retrieval.embedding.dimensions", "0")));
    }

    @Test
    void rejectsOverlapNotLessThanChunkSize() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of(
                        "archivist.retrieval.embedding.chunk-size-chars", "200",
                        "archivist.retrieval.embedding.chunk-overlap-chars", "200")));
    }

    @Test
    void rejectsNegativeIndexTtl() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of("archivist.retrieval.embedding.index-ttl", "PT-1S")));
    }

    private static EmbeddingProperties bind(Map<String, Object> values) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        RetrievalProperties retrieval = new Binder(new MapConfigurationPropertySource(values))
                .bind("archivist.retrieval", Bindable.of(RetrievalProperties.class))
                .get();
        var violations = validator.validate(retrieval);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.toString());
        }
        return retrieval.getEmbedding();
    }
}
