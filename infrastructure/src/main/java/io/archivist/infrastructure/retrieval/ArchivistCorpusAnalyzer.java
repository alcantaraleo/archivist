package io.archivist.infrastructure.retrieval;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

final class ArchivistCorpusAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new ArchivistTokenizer());
    }

    private static final class ArchivistTokenizer extends Tokenizer {

        private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
        private Iterator<String> tokens;
        private String pendingText;

        @Override
        public void reset() throws IOException {
            super.reset();
            tokens = null;
            pendingText = null;
        }

        @Override
        public boolean incrementToken() {
            if (tokens == null) {
                if (pendingText == null) {
                    try {
                        pendingText = readInput();
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read token input", exception);
                    }
                }
                List<String> tokenList = RetrievalTokenization.tokenize(pendingText);
                tokens = tokenList.iterator();
            }
            if (!tokens.hasNext()) {
                return false;
            }
            clearAttributes();
            termAttribute.setEmpty().append(tokens.next());
            return true;
        }

        private String readInput() throws IOException {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[512];
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
