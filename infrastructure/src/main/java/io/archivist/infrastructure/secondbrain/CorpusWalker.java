package io.archivist.infrastructure.secondbrain;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class CorpusWalker {

    private final List<PathMatcher> ignoreMatchers;

    CorpusWalker(List<String> ignoreGlobs) {
        var fileSystem = FileSystems.getDefault();
        this.ignoreMatchers = ignoreGlobs.stream()
                .map(glob -> fileSystem.getPathMatcher("glob:" + glob))
                .toList();
    }

    List<Path> discoverMarkdownFiles(Path corpusRoot) throws IOException {
        List<Path> discovered = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(corpusRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !hasHiddenSegment(corpusRoot, path))
                    .filter(path -> !matchesIgnoreGlob(corpusRoot, path))
                    .forEach(discovered::add);
        }
        return discovered;
    }

    private boolean hasHiddenSegment(Path corpusRoot, Path filePath) {
        Path relative = corpusRoot.relativize(filePath);
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesIgnoreGlob(Path corpusRoot, Path filePath) {
        Path relative = corpusRoot.relativize(filePath);
        for (PathMatcher matcher : ignoreMatchers) {
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }
}
