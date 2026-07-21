package io.archivist.infrastructure.retrieval;

import jakarta.validation.constraints.Min;

class HybridProperties {

    @Min(1)
    private int maxResults = 20;

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
