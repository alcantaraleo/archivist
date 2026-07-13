package io.archivist.infrastructure.secondbrain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archivist.second-brain")
public class SecondBrainCorpusProperties {

    private String path;
    private long maxEntryBytes = 1_048_576L;
    private int metadataReadBytes = 65_536;
    private int loadConcurrency = 32;
    private List<String> ignoreGlobs = new ArrayList<>(List.of("**/templates/**", "**/.trash/**"));

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getMaxEntryBytes() {
        return maxEntryBytes;
    }

    public void setMaxEntryBytes(long maxEntryBytes) {
        this.maxEntryBytes = maxEntryBytes;
    }

    public int getMetadataReadBytes() {
        return metadataReadBytes;
    }

    public void setMetadataReadBytes(int metadataReadBytes) {
        this.metadataReadBytes = metadataReadBytes;
    }

    public int getLoadConcurrency() {
        return loadConcurrency;
    }

    public void setLoadConcurrency(int loadConcurrency) {
        this.loadConcurrency = loadConcurrency;
    }

    public List<String> getIgnoreGlobs() {
        return ignoreGlobs;
    }

    public void setIgnoreGlobs(List<String> ignoreGlobs) {
        this.ignoreGlobs = ignoreGlobs;
    }
}
