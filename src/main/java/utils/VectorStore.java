package utils;

import java.util.List;
import java.util.Map;

/**
 * VectorStore interface for embedding storage and similarity search.
 * Designed for both in-memory and persistent vector DBs.
 */
public interface VectorStore {

    /**
     * Encapsulates search result information
     */
    class SearchResult {
        public final String id;                   // ID of reference or test case
        public final float similarity;            // Cosine similarity
        public final Map<String, Object> metadata; // Optional metadata like referenceAnswer, source

        public SearchResult(String id, float similarity, Map<String, Object> metadata) {
            this.id = id;
            this.similarity = similarity;
            this.metadata = metadata != null ? metadata : Map.of();
        }
    }

    /**
     * Insert or update a vector in the store.
     *
     * @param id       Unique ID
     * @param vector   Embedding vector
     * @param metadata Optional metadata
     */
    void upsert(String id, float[] vector, Map<String, Object> metadata);

    /**
     * Search for top K nearest vectors using cosine similarity.
     *
     * @param vector Query vector
     * @param topK   Number of results
     * @return List of SearchResult sorted descending by similarity
     */
    List<SearchResult> search(float[] vector, int topK);
}