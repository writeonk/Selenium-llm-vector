package utils;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class EmbeddingService {

    private static final Logger logger = LogManager.getLogger(EmbeddingService.class);

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    /**
     * Initialize embedding model using DJL + PyTorch engine (tokenizer-based).
     */
    public void init() throws IOException, ModelException {
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optEngine("PyTorch")
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            logger.info("Embedding model loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load embedding model: {}", e.getMessage(), e);
            throw new IOException("Embedding model load failed", e);
        }
    }

    /**
     * Get embedding vector for a text string.
     */
    public float[] embed(String text) throws TranslateException {
        if (predictor == null) {
            throw new IllegalStateException("Predictor not initialized. Call init() first.");
        }
        return predictor.predict(text);
    }

    /**
     * Close model and free resources.
     */
    public void close() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
        logger.info("EmbeddingService closed.");
    }

    /**
     * Utility: cosine similarity between two vectors
     */
    public static double cosine(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }
}