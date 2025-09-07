package utils;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import io.github.cdimascio.dotenv.Dotenv;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.IOException;
import java.util.*;

public class HuggingFaceLLMService implements LLMService {

    private final ZooModel<String, float[]> embeddingModel;
    private final Predictor<String, float[]> predictor;
    private final String usedModel;

    private static final Dotenv dotenv = Dotenv.load();
    private static final String HF_TOKEN = dotenv.get("HF_TOKEN");

    private static final List<String> VERIFIED_MODELS = List.of(
            "sentence-transformers/all-MiniLM-L6-v2",
            "sentence-transformers/all-mpnet-base-v2",
            "sentence-transformers/all-distilroberta-v1"
    );

    public HuggingFaceLLMService(String primaryModel) throws IOException, ModelException {
        ZooModel<String, float[]> model = null;
        String selectedModel = null;

        for (String modelName : VERIFIED_MODELS) {
            try {
                System.out.println("[INFO] Attempting to load HuggingFace model: " + modelName);

                Criteria<String, float[]> criteria = Criteria.builder()
                        .setTypes(String.class, float[].class)
                        .optEngine("PyTorch")
                        .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                        .optOption("apiAccessToken", HF_TOKEN)
                        .build();

                model = criteria.loadModel();
                selectedModel = modelName;
                break;
            } catch (ModelException e) {
                System.err.println("[WARN] Failed to load model: " + modelName + ", trying next...");
            }
        }

        if (model == null) {
            throw new ModelException("No accessible HuggingFace model could be loaded.");
        }

        embeddingModel = model;
        predictor = embeddingModel.newPredictor();
        usedModel = selectedModel;
        System.out.println("[INFO] Successfully loaded model: " + usedModel);
    }

    @Override
    public Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer) {
        Map<String, Object> scores = new HashMap<>();
        try {
            // ----------------- Tokenization -----------------
            SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
            String[] refTokens = tokenizer.tokenize(normalize(referenceAnswer));
            String[] botTokens = tokenizer.tokenize(normalize(botAnswer));

            // ----------------- Semantic Similarity -----------------
            float[] botVector = predictor.predict(botAnswer);
            float[] refVector = (referenceAnswer != null && !referenceAnswer.isEmpty())
                    ? predictor.predict(referenceAnswer)
                    : null;

            double semanticSim = (refVector != null) ? cosineSimilarity(botVector, refVector) : 0.5;
            semanticSim = Math.max(0.0, Math.min(1.0, semanticSim));

            // ----------------- BLEU-4 (smoothed) -----------------
            double bleu4 = computeBLEU(refTokens, botTokens, 4);

            // ----------------- ROUGE-L -----------------
            double rougeL = computeROUGEL(refTokens, botTokens);

            // ----------------- F1 Score -----------------
            double f1 = computeF1(refTokens, botTokens);

            // ----------------- Exact Match -----------------
            boolean exactMatch = normalize(referenceAnswer).equals(normalize(botAnswer));

            // ----------------- Confidence & Hallucination -----------------
            double confidence = semanticSim;
            double hallucinationRisk = 1.0 - semanticSim;

            // ----------------- Composite Score (Balanced) -----------------
            double finalScore =
                    0.45 * semanticSim +   // meaning match (core)
                            0.30 * rougeL +        // sequence overlap (coverage)
                            0.15 * f1 +            // token-level overlap
                            0.10 * bleu4;          // exact n-gram match

            // ----------------- Save Scores -----------------
            scores.put("semanticSimilarity", semanticSim);
            scores.put("bleu4", bleu4);
            scores.put("rougeL", rougeL);
            scores.put("f1", f1);
            scores.put("exactMatch", exactMatch ? 1.0 : 0.0);
            scores.put("confidence", confidence);
            scores.put("hallucinationRisk", hallucinationRisk);
            scores.put("finalScore", finalScore);
            scores.put("usedModel", usedModel);

        } catch (Exception e) {
            // ----------------- Fallback Safe Defaults -----------------
            scores.put("semanticSimilarity", 0.0);
            scores.put("bleu4", 0.0);
            scores.put("rougeL", 0.0);
            scores.put("f1", 0.0);
            scores.put("exactMatch", 0.0);
            scores.put("confidence", 0.0);
            scores.put("hallucinationRisk", 1.0);
            scores.put("finalScore", 0.0);
            scores.put("usedModel", usedModel);
        }
        return scores;
    }

    // ----------------- Helpers -----------------

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
    }

    private double computeBLEU(String[] ref, String[] hyp, int maxN) {
        double score = 1.0;
        for (int n = 1; n <= maxN; n++) {
            double overlap = ngramOverlap(ref, hyp, n);
            double total = Math.max(hyp.length - n + 1, 1);
            double precision = (overlap + 1.0) / (total + 1.0); // ✅ smoothing
            score *= precision;
        }
        return Math.pow(score, 1.0 / maxN);
    }

    private int ngramOverlap(String[] ref, String[] hyp, int n) {
        Map<String, Integer> refNgrams = ngrams(ref, n);
        Map<String, Integer> hypNgrams = ngrams(hyp, n);
        int overlap = 0;
        for (String ng : hypNgrams.keySet()) {
            overlap += Math.min(hypNgrams.get(ng), refNgrams.getOrDefault(ng, 0));
        }
        return overlap;
    }

    private Map<String, Integer> ngrams(String[] tokens, int n) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i <= tokens.length - n; i++) {
            String ng = String.join(" ", Arrays.copyOfRange(tokens, i, i + n));
            map.put(ng, map.getOrDefault(ng, 0) + 1);
        }
        return map;
    }

    private double computeROUGEL(String[] ref, String[] hyp) {
        int lcs = longestCommonSubsequence(ref, hyp);
        return (double) lcs / Math.max(ref.length, 1); // ✅ safe
    }

    private int longestCommonSubsequence(String[] a, String[] b) {
        int[][] dp = new int[a.length + 1][b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                if (a[i - 1].equals(b[j - 1]))
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                else
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[a.length][b.length];
    }

    private double computeF1(String[] ref, String[] hyp) {
        Set<String> refSet = new HashSet<>(Arrays.asList(ref));
        Set<String> hypSet = new HashSet<>(Arrays.asList(hyp));

        int overlap = 0;
        for (String token : hypSet) {
            if (refSet.contains(token)) overlap++;
        }
        double precision = (double) overlap / Math.max(hypSet.size(), 1);
        double recall = (double) overlap / Math.max(refSet.size(), 1);
        return (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
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
}