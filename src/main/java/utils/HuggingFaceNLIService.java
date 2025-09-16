package utils;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HuggingFaceNLIService
 * <p>
 * Evaluates premise-hypothesis pairs and returns dynamic probabilities for
 * contradiction, entailment, and neutral.
 */
public class HuggingFaceNLIService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceNLIService.class);

    private final ZooModel<String[], float[]> nliModel;
    private final Predictor<String[], float[]> predictor;
    private static final Dotenv dotenv = Dotenv.load();
    private static final String HF_TOKEN = dotenv.get("HF_TOKEN");

    public HuggingFaceNLIService() throws IOException, ModelException {
        ZooModel<String[], float[]> model = null;
        try {
            Criteria<String[], float[]> criteria = Criteria.builder()
                    .setTypes(String[].class, float[].class)
                    .optEngine("PyTorch")
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/facebook/bart-large-mnli")
                    .optOption("apiAccessToken", HF_TOKEN)
                    .build();
            model = criteria.loadModel();
            log.info("Loaded HuggingFace NLI model: facebook/bart-large-mnli");
        } catch (Exception e) {
            log.error("Failed to load NLI model: {}", e.getMessage());
            throw e;
        }
        this.nliModel = model;
        this.predictor = nliModel.newPredictor();
    }

    /**
     * Evaluates a single premise-hypothesis pair
     *
     * @param premise    reference text
     * @param hypothesis bot answer
     * @return Map with "contradiction", "entailment", "neutral" probabilities
     */
    public Map<String, Double> evaluate(String premise, String hypothesis) throws TranslateException {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("contradiction", 0.0);
        result.put("entailment", 0.0);
        result.put("neutral", 0.0);

        if (premise == null || hypothesis == null || premise.isBlank() || hypothesis.isBlank()) {
            return result; // fallback
        }

        try {
            String[] input = new String[]{premise, hypothesis};
            float[] logits = predictor.predict(input); // shape [3]
            double[] probs = softmax(logits);

            result.put("contradiction", probs[0]);
            result.put("entailment", probs[1]);
            result.put("neutral", probs[2]);
        } catch (Exception e) {
            log.warn("NLI evaluation failed: {}; returning fallback zeros", e.getMessage());
        }
        return result;
    }

    private double[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float l : logits) max = Math.max(max, l);

        double sum = 0.0;
        double[] exps = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exps[i] = Math.exp(logits[i] - max);
            sum += exps[i];
        }
        for (int i = 0; i < logits.length; i++) exps[i] /= sum;

        return exps;
    }

    @Override
    public void close() {
        try {
            if (predictor != null) predictor.close();
        } catch (Exception ignored) {
        }
        try {
            if (nliModel != null) nliModel.close();
        } catch (Exception ignored) {
        }
    }
}