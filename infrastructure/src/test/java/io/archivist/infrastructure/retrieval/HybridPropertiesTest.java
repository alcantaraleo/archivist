package io.archivist.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class HybridPropertiesTest {

    @Test
    void defaultsMatchContract() {
        HybridProperties properties = new RetrievalProperties().getHybrid();

        assertEquals(20, properties.getMaxResults());
    }

    @Test
    void bindsNestedMaxResults() {
        HybridProperties properties = bind(Map.of("archivist.retrieval.hybrid.max-results", "7"));

        assertEquals(7, properties.getMaxResults());
    }

    @Test
    void rejectsNonPositiveMaxResults() {
        assertThrows(
                Exception.class,
                () -> bind(Map.of("archivist.retrieval.hybrid.max-results", "0")));
    }

    private static HybridProperties bind(Map<String, Object> values) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        RetrievalProperties retrieval = new Binder(new MapConfigurationPropertySource(values))
                .bind("archivist.retrieval", Bindable.of(RetrievalProperties.class))
                .get();
        var violations = validator.validate(retrieval);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.toString());
        }
        return retrieval.getHybrid();
    }
}
