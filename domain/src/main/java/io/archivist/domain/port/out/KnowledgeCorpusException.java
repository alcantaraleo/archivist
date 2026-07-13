package io.archivist.domain.port.out;

public class KnowledgeCorpusException extends RuntimeException {

    public KnowledgeCorpusException(String message) {
        super(message);
    }

    public KnowledgeCorpusException(String message, Throwable cause) {
        super(message, cause);
    }
}
