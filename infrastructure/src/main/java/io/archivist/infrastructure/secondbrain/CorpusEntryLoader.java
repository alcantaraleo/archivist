package io.archivist.infrastructure.secondbrain;

import io.archivist.domain.model.ContentAvailability;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CorpusEntryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorpusEntryLoader.class);

    private final Path corpusRoot;
    private final long maxEntryBytes;
    private final int metadataReadBytes;
    private final MetadataEnvelopeParser metadataEnvelopeParser;
    private final CorpusEntryMapper corpusEntryMapper;

    CorpusEntryLoader(
            Path corpusRoot,
            long maxEntryBytes,
            int metadataReadBytes,
            MetadataEnvelopeParser metadataEnvelopeParser,
            CorpusEntryMapper corpusEntryMapper) {
        this.corpusRoot = corpusRoot;
        this.maxEntryBytes = maxEntryBytes;
        this.metadataReadBytes = metadataReadBytes;
        this.metadataEnvelopeParser = metadataEnvelopeParser;
        this.corpusEntryMapper = corpusEntryMapper;
    }

    Optional<CorpusEntryMapper.MappedEntry> load(Path filePath) {
        try {
            long fileSize = Files.size(filePath);
            Instant fileModifiedTime = Files.getLastModifiedTime(filePath).toInstant();

            if (fileSize > maxEntryBytes) {
                LOGGER.info(
                        "Entry exceeds max-entry-bytes ({} > {}): {}",
                        fileSize,
                        maxEntryBytes,
                        corpusRoot.relativize(filePath));
                int readLength = (int) Math.min(fileSize, metadataReadBytes);
                byte[] prefix = readPrefix(filePath, readLength);
                MetadataEnvelopeParser.ParseResult parsed = metadataEnvelopeParser.parseBytes(prefix, prefix.length);
                return mapOrWarn(filePath, parsed.metadata(), "", ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE, fileModifiedTime);
            }

            String text = Files.readString(filePath);
            MetadataEnvelopeParser.ParseResult parsed = metadataEnvelopeParser.parse(text);
            return mapOrWarn(
                    filePath,
                    parsed.metadata(),
                    parsed.body(),
                    ContentAvailability.AVAILABLE,
                    fileModifiedTime);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read entry {}: {}", corpusRoot.relativize(filePath), exception.getMessage());
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to parse entry {}: {}",
                    corpusRoot.relativize(filePath),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private byte[] readPrefix(Path filePath, int readLength) throws IOException {
        byte[] prefix = new byte[readLength];
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            int offset = 0;
            while (offset < readLength) {
                int read = inputStream.read(prefix, offset, readLength - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset < readLength) {
                byte[] truncated = new byte[offset];
                System.arraycopy(prefix, 0, truncated, 0, offset);
                return truncated;
            }
        }
        return prefix;
    }

    private Optional<CorpusEntryMapper.MappedEntry> mapOrWarn(
            Path filePath,
            Map<String, Object> metadata,
            String body,
            ContentAvailability availability,
            Instant fileModifiedTime) {
        Optional<CorpusEntryMapper.MappedEntry> mapped =
                corpusEntryMapper.map(corpusRoot, filePath, metadata, body, availability, fileModifiedTime);
        if (mapped.isEmpty()) {
            LOGGER.warn("Omitting unrecoverable entry {}", corpusRoot.relativize(filePath));
        }
        return mapped;
    }
}
