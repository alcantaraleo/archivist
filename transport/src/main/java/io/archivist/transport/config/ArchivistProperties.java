package io.archivist.transport.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "archivist")
public class ArchivistProperties {

    @Valid
    private SecondBrain secondBrain = new SecondBrain();

    public SecondBrain getSecondBrain() {
        return secondBrain;
    }

    public void setSecondBrain(SecondBrain secondBrain) {
        this.secondBrain = secondBrain;
    }

    @AssertTrue(message = "archivist.second-brain.path (ARCHIVIST_SECOND_BRAIN_PATH) must point to an existing directory")
    public boolean isSecondBrainPathDirectory() {
        String path = secondBrain.getPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        return Files.isDirectory(Paths.get(path));
    }

    public static class SecondBrain {

        @NotBlank(message = "archivist.second-brain.path (ARCHIVIST_SECOND_BRAIN_PATH) is required and must not be blank")
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
