package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class Bm25PropertiesTest {

    @Test
    void defaultsMatchContract() {
        Bm25Properties properties = new RetrievalProperties().getBm25();

        assertEquals(3.0f, properties.getFieldBoostTitle());
        assertEquals(2.0f, properties.getFieldBoostTags());
        assertEquals(1.0f, properties.getFieldBoostBody());
        assertEquals(Duration.ofMinutes(15), properties.getIndexTtl());
        assertEquals(1.2f, properties.getK1());
        assertEquals(0.75f, properties.getB());
    }

    @Test
    void rejectsNonPositiveFieldBoost() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of("archivist.retrieval.bm25.field-boost-title", "0")));
    }

    @Test
    void rejectsInvalidB() {
        assertThrows(Exception.class, () -> bind(Map.of("archivist.retrieval.bm25.b", "1.5")));
    }

    @Test
    void rejectsNegativeIndexTtl() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of("archivist.retrieval.bm25.index-ttl", "PT-1S")));
    }

    private static Bm25Properties bind(java.util.Map<String, Object> values) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        RetrievalProperties retrieval = new Binder(new MapConfigurationPropertySource(values))
                .bind("archivist.retrieval", Bindable.of(RetrievalProperties.class))
                .get();
        var violations = validator.validate(retrieval);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.toString());
        }
        return retrieval.getBm25();
    }
}
