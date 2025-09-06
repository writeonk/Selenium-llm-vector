package utils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory implementation of VectorStore.
 * Ideal for testing or small-scale production scenarios.
 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, float[]> vectors = new HashMap<>();
    private final Map<String, Map<String, Object>> metadataMap = new HashMap<>();

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> metadata) {
        if (id == null || vector == null) {
            throw new IllegalArgumentException("Vector ID and vector must not be null.");
        }
        vectors.put(id, vector);
        metadataMap.put(id, metadata != null ? metadata : Map.of());
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
        if (queryVector == null || topK <= 0) return List.of();

        return vectors.entrySet().stream()
                .map(e -> new SearchResult(
                        e.getKey(),
                        cosineSimilarity(queryVector, e.getValue()),
                        metadataMap.get(e.getKey())
                ))
                .sorted((a, b) -> Float.compare(b.similarity, a.similarity))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Cosine similarity between two vectors with numerical stability.
     */
    private float cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA.length != vecB.length) return 0f;

        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10));
    }
}