package io.archivist.infrastructure.retrieval;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

class Bm25Properties {

    @DecimalMin(value = "0.0", inclusive = false)
    private float fieldBoostTitle = 3.0f;

    @DecimalMin(value = "0.0", inclusive = false)
    private float fieldBoostTags = 2.0f;

    @DecimalMin(value = "0.0", inclusive = false)
    private float fieldBoostBody = 1.0f;

    @NotNull
    private Duration indexTtl = Duration.ofMinutes(15);

    @DecimalMin(value = "0.0", inclusive = false)
    private float k1 = 1.2f;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private float b = 0.75f;

    public float getFieldBoostTitle() {
        return fieldBoostTitle;
    }

    public void setFieldBoostTitle(float fieldBoostTitle) {
        this.fieldBoostTitle = fieldBoostTitle;
    }

    public float getFieldBoostTags() {
        return fieldBoostTags;
    }

    public void setFieldBoostTags(float fieldBoostTags) {
        this.fieldBoostTags = fieldBoostTags;
    }

    public float getFieldBoostBody() {
        return fieldBoostBody;
    }

    public void setFieldBoostBody(float fieldBoostBody) {
        this.fieldBoostBody = fieldBoostBody;
    }

    public Duration getIndexTtl() {
        return indexTtl;
    }

    public void setIndexTtl(Duration indexTtl) {
        this.indexTtl = indexTtl;
    }

    public float getK1() {
        return k1;
    }

    public void setK1(float k1) {
        this.k1 = k1;
    }

    public float getB() {
        return b;
    }

    public void setB(float b) {
        this.b = b;
    }

    @AssertTrue(message = "archivist.retrieval.bm25.index-ttl must not be negative")
    public boolean isIndexTtlNonNegative() {
        return indexTtl != null && !indexTtl.isNegative();
    }
}
