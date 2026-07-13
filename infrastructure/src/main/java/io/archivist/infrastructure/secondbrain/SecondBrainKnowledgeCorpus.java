package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import io.archivist.domain.port.out.KnowledgeCorpusException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecondBrainKnowledgeCorpus implements KnowledgeCorpus {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondBrainKnowledgeCorpus.class);

    private final Path corpusRoot;
    private final int loadConcurrency;
    private final CorpusWalker corpusWalker;
    private final CorpusEntryLoader corpusEntryLoader;

    public SecondBrainKnowledgeCorpus(
            Path corpusRoot,
            int loadConcurrency,
            CorpusWalker corpusWalker,
            CorpusEntryLoader corpusEntryLoader) {
        this.corpusRoot = corpusRoot;
        this.loadConcurrency = loadConcurrency;
        this.corpusWalker = corpusWalker;
        this.corpusEntryLoader = corpusEntryLoader;
    }

    @Override
    public List<Provenance> catalog() {
        return loadMappedEntries().stream().map(CorpusEntryMapper.MappedEntry::provenance).toList();
    }

    @Override
    public Optional<Evidence> loadBySourceId(String sourceId) {
        if (sourceId == null) {
            throw new NullPointerException("sourceId must not be null");
        }
        if (sourceId.isBlank()) {
            return Optional.empty();
        }
        return loadMappedEntries().stream()
                .filter(entry -> entry.provenance().sourceId().equals(sourceId))
                .findFirst()
                .map(CorpusEntryMapper.MappedEntry::toEvidence);
    }

    @Override
    public List<Evidence> loadAll() {
        return loadMappedEntries().stream().map(CorpusEntryMapper.MappedEntry::toEvidence).toList();
    }

    private List<CorpusEntryMapper.MappedEntry> loadMappedEntries() {
        ensureReadableCorpusRoot();
        List<Path> markdownFiles;
        try {
            markdownFiles = corpusWalker.discoverMarkdownFiles(corpusRoot);
        } catch (IOException exception) {
            throw new KnowledgeCorpusException("Failed to walk corpus root: " + corpusRoot, exception);
        }

        Semaphore semaphore = new Semaphore(loadConcurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Optional<CorpusEntryMapper.MappedEntry>>> tasks = markdownFiles.stream()
                    .map(filePath -> (Callable<Optional<CorpusEntryMapper.MappedEntry>>) () -> {
                        semaphore.acquire();
                        try {
                            return corpusEntryLoader.load(filePath);
                        } finally {
                            semaphore.release();
                        }
                    })
                    .toList();

            List<Future<Optional<CorpusEntryMapper.MappedEntry>>> futures = executor.invokeAll(tasks);
            Map<String, CorpusEntryMapper.MappedEntry> entriesBySourceId = new LinkedHashMap<>();
            for (Future<Optional<CorpusEntryMapper.MappedEntry>> future : futures) {
                try {
                    Optional<CorpusEntryMapper.MappedEntry> mapped = future.get();
                    if (mapped.isEmpty()) {
                        continue;
                    }
                    String sourceId = mapped.get().provenance().sourceId();
                    if (entriesBySourceId.containsKey(sourceId)) {
                        LOGGER.warn("Duplicate sourceId {}; keeping first occurrence", sourceId);
                        continue;
                    }
                    entriesBySourceId.put(sourceId, mapped.get());
                } catch (ExecutionException exception) {
                    throw new KnowledgeCorpusException("Failed to load corpus entry", exception.getCause());
                }
            }
            return List.copyOf(new ArrayList<>(entriesBySourceId.values()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KnowledgeCorpusException("Corpus load interrupted", exception);
        }
    }

    private void ensureReadableCorpusRoot() {
        if (!Files.isDirectory(corpusRoot)) {
            throw new KnowledgeCorpusException("Corpus root is not readable: " + corpusRoot);
        }
    }
}
