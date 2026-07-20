package io.archivist.infrastructure.retrieval;

import io.archivist.domain.model.ContentAvailability;
import io.archivist.domain.model.Evidence;
import io.archivist.domain.model.Provenance;
import io.archivist.domain.port.out.KnowledgeCorpus;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

final class Bm25IndexBuilder {

    static final String FIELD_SOURCE_ID = "sourceId";
    static final String FIELD_TITLE = "title";
    static final String FIELD_TAGS = "tags";
    static final String FIELD_BODY = "body";
    static final String FIELD_KNOWLEDGE_TYPE = "knowledgeType";
    static final String FIELD_KNOWLEDGE_ZONE = "knowledgeZone";

    BuiltIndex build(KnowledgeCorpus corpus, Bm25Properties properties) throws IOException {
        // Last Evidence wins per sourceId — one Lucene doc + one map entry (avoids stale hit mapping).
        Map<String, Evidence> evidenceBySourceId = new LinkedHashMap<>();
        for (Evidence evidence : corpus.loadAll()) {
            evidenceBySourceId.put(evidence.provenance().sourceId(), evidence);
        }

        Directory directory = new ByteBuffersDirectory();
        ArchivistCorpusAnalyzer analyzer = new ArchivistCorpusAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity(properties.getK1(), properties.getB()));

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Evidence evidence : evidenceBySourceId.values()) {
                writer.addDocument(toDocument(evidence));
            }
        }

        DirectoryReader reader = DirectoryReader.open(directory);
        return new BuiltIndex(directory, reader, Map.copyOf(evidenceBySourceId));
    }

    private static Document toDocument(Evidence evidence) {
        Provenance provenance = evidence.provenance();
        Document document = new Document();
        document.add(new StringField(FIELD_SOURCE_ID, provenance.sourceId(), Field.Store.YES));
        document.add(new TextField(FIELD_TITLE, nullToEmpty(provenance.title()), Field.Store.NO));
        document.add(new TextField(FIELD_TAGS, joinTags(provenance.tags()), Field.Store.NO));

        String body = "";
        if (provenance.contentAvailability() == ContentAvailability.AVAILABLE) {
            body = nullToEmpty(evidence.content());
        }
        document.add(new TextField(FIELD_BODY, body, Field.Store.NO));

        document.add(new StringField(
                FIELD_KNOWLEDGE_TYPE, provenance.type().name(), Field.Store.NO));
        document.add(new StringField(
                FIELD_KNOWLEDGE_ZONE, provenance.zone().name(), Field.Store.NO));
        return document;
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record BuiltIndex(Directory directory, DirectoryReader reader, Map<String, Evidence> evidenceBySourceId)
            implements AutoCloseable {

        @Override
        public void close() throws IOException {
            reader.close();
            directory.close();
        }
    }
}
