package utils;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.TranslateException;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HuggingFaceLLMService implements LLMService {

    private final ZooModel<String, float[]> embeddingModel;
    private final Predictor<String, float[]> predictor;

    // Load HF token from .env
    private static final Dotenv dotenv = Dotenv.load();
    private static final String HF_TOKEN = dotenv.get("HF_TOKEN");

    public HuggingFaceLLMService(String backboneModelName) throws ModelException, IOException {
        System.out.println("[INFO] Loading HuggingFace model: " + backboneModelName);

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optEngine("PyTorch")
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + backboneModelName)
                .optOption("apiAccessToken", HF_TOKEN)
                .optTranslator(new FeatureExtractionTranslator())
                .build();

        embeddingModel = criteria.loadModel();
        predictor = embeddingModel.newPredictor();
        System.out.println("[INFO] Model loaded successfully ✅: " + backboneModelName);
    }

    @Override
    public Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer) {
        Map<String, Object> scores = new HashMap<>();
        try {
            float[] botVector = predictor.predict(botAnswer);
            float[] refVector = (referenceAnswer != null && !referenceAnswer.isEmpty())
                    ? predictor.predict(referenceAnswer)
                    : null;

            double similarity = (refVector != null) ? cosineSimilarity(botVector, refVector) : 0.5;

            scores.put("relevance", similarity);
            scores.put("hallucinationRisk", 1.0 - similarity);
            scores.put("confidence", similarity);

        } catch (TranslateException e) {
            scores.put("relevance", 0.0);
            scores.put("hallucinationRisk", 1.0);
            scores.put("confidence", 0.0);
        }
        return scores;
    }

    private double cosineSimilarity(float[] vecA, float[] vecB) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    public void close() {
        if (predictor != null) predictor.close();
        if (embeddingModel != null) embeddingModel.close();
    }

    public static class FeatureExtractionTranslator implements Translator<String, float[]> {
        @Override
        public NDList processInput(TranslatorContext ctx, String input) {
            // DJL HuggingFace tokenizer handles input internally
            return new NDList(ctx.getNDManager().create(new float[0]));
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            return list.singletonOrThrow().toFloatArray();
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}