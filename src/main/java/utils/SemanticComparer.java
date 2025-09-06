package utils;

import java.util.List;
import java.util.Objects;

/**
 * Compares chatbot answers against reference embeddings
 * using VectorStore and cosine similarity.
 */
public class SemanticComparer {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public SemanticComparer(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "EmbeddingService cannot be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null");
    }

    /**
     * Result wrapper for semantic comparison.
     */
    public static class ComparisonResult {
        public boolean pass;
        public String matchedReference;
        public double similarity;
    }

    /**
     * Compares bot answer to nearest reference vectors.
     *
     * @param question  User question (optional, for logging)
     * @param botAnswer Bot response
     * @param topK      Number of nearest references to consider
     * @param threshold Similarity threshold to pass
     * @return ComparisonResult with pass/fail, similarity, and reference matched
     */
    public ComparisonResult compareToNearest(String question, String botAnswer, int topK, double threshold) {
        ComparisonResult result = new ComparisonResult();
        result.pass = false;
        result.similarity = 0.0;
        result.matchedReference = null;

        if (botAnswer == null || botAnswer.isEmpty()) return result;

        float[] botEmbedding;
        try {
            botEmbedding = embeddingService.embed(botAnswer);
        } catch (Exception e) {
            return result; // Graceful fail if embedding fails
        }

        List<VectorStore.SearchResult> nearest = vectorStore.search(botEmbedding, topK);
        if (nearest.isEmpty()) return result;

        VectorStore.SearchResult best = nearest.get(0);
        Object ref = best.metadata.get("referenceAnswer");
        result.matchedReference = ref != null ? ref.toString() : null;
        result.similarity = best.similarity;
        result.pass = best.similarity >= threshold;

        return result;
    }
}