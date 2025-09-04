package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class SemanticComparer {

    private static final Logger logger = LogManager.getLogger(SemanticComparer.class);

    private final EmbeddingService embedder;
    private final VectorStore store;

    public SemanticComparer(EmbeddingService embedder, VectorStore store) {
        this.embedder = embedder;
        this.store = store;
        logger.info("SemanticComparer initialized.");
    }

    public ComparisonResult compareToNearest(String question, String actualAnswer, int topK, double threshold) throws Exception {
        logger.info("Comparing semantic similarity for question: {}", question);

        float[] qVec = embedder.embed(question);
        List<VectorStore.ScoredReference> hits = store.search(qVec, topK);

        float[] actualVec = embedder.embed(actualAnswer);
        double best = -1;
        String bestRef = null;

        for (var hit : hits) {
            float[] refVec = embedder.embed(hit.referenceAnswer);
            double sim = EmbeddingService.cosine(refVec, actualVec);
            logger.debug("Similarity [{}] = {}", hit.referenceAnswer, sim);

            if (sim > best) {
                best = sim;
                bestRef = hit.referenceAnswer;
            }
        }

        boolean pass = best >= threshold;
        logger.info(pass ? "PASS | Best similarity: {} matched reference: {}"
                : "FAIL | Best similarity: {} matched reference: {}", best, bestRef);

        return new ComparisonResult(pass, best, bestRef);
    }

    public static class ComparisonResult {
        public final boolean pass;
        public final double similarity;
        public final String matchedReference;

        public ComparisonResult(boolean pass, double similarity, String matchedReference) {
            this.pass = pass;
            this.similarity = similarity;
            this.matchedReference = matchedReference;
        }
    }
}