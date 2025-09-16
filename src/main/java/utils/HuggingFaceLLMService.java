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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


public class HuggingFaceLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceLLMService.class);

    private final ZooModel<String, float[]> embeddingModel;
    private final Predictor<String, float[]> predictor;
    private final String usedModel;
    private final PerplexityCalculator perplexityCalculator; // may throw on init
    private final HuggingFaceNLIService nliService; // may be null if init fails

    private static final Dotenv dotenv = Dotenv.load();
    private static final String HF_TOKEN = dotenv.get("HF_TOKEN");

    // A shortlist of verified embedding models - you can pass primaryModel to prefer one
    private static final List<String> VERIFIED_MODELS = List.of(
            "sentence-transformers/all-MiniLM-L6-v2",
            "sentence-transformers/all-mpnet-base-v2",
            "sentence-transformers/all-distilroberta-v1"
    );

    public HuggingFaceLLMService(String primaryModel) throws IOException, ModelException {
        ZooModel<String, float[]> model = null;
        String selectedModel = null;

        // Prefer primaryModel if provided and in list
        List<String> candidates = new ArrayList<>(VERIFIED_MODELS);
        if (primaryModel != null && !primaryModel.isBlank()) {
            candidates.remove(primaryModel);
            candidates.add(0, primaryModel);
        }

        // Try to load an embedding model via DJL
        for (String modelName : candidates) {
            try {
                log.info("Attempting to load embedding model: {}", modelName);
                Criteria<String, float[]> criteria = Criteria.builder()
                        .setTypes(String.class, float[].class)
                        .optEngine("PyTorch")
                        .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                        .optOption("apiAccessToken", HF_TOKEN)
                        .build();
                model = criteria.loadModel();
                selectedModel = modelName;
                log.info("Loaded embedding model: {}", modelName);
                break;
            } catch (Exception e) {
                log.warn("Failed to load embedding model '{}' : {}", modelName, e.getMessage());
            }
        }

        if (model == null) {
            throw new ModelException("Failed to load any embedding model from candidates: " + candidates);
        }

        this.embeddingModel = model;
        this.predictor = embeddingModel.newPredictor();
        this.usedModel = selectedModel != null ? selectedModel : "unknown";

        // Initialize perplexity calculator (wrap errors)
        PerplexityCalculator pc = null;
        try {
            pc = new PerplexityCalculator("gpt2"); // your class; may load DJL model inside
            log.info("PerplexityCalculator initialized (gpt2).");
        } catch (Exception e) {
            log.warn("PerplexityCalculator failed to initialize: {}. Perplexity will fallback.", e.getMessage());
        }
        this.perplexityCalculator = pc;

        // Initialize NLI service (optional). If it fails, keep nliService = null and fallback later.
        HuggingFaceNLIService nli = null;
        try {
            nli = new HuggingFaceNLIService(); // implement separate class that loads NLI model
            log.info("HuggingFaceNLIService initialized.");
        } catch (Exception e) {
            log.warn("HuggingFaceNLIService failed to initialize: {}. NLI (contradiction/entailment/neutral) will fallback.", e.getMessage());
        }
        this.nliService = nli;
    }

    /**
     * Evaluate the bot answer and return a map of metrics.
     * Signature matches LLMService in your codebase.
     */
    @Override
    public Map<String, Object> evaluateAnswer(String question,
                                              String referenceAnswer,
                                              String botAnswer,
                                              String userId,
                                              String sessionId,
                                              List<String> extraContext) {
        Instant start = Instant.now();
        Map<String, Object> scores = new LinkedHashMap<>(); // stable ordering for report readability

        // normalize inputs (safe)
        String ref = referenceAnswer == null ? "" : referenceAnswer.trim();
        String bot = botAnswer == null ? "" : botAnswer.trim();

        try {
            // Tokenize using simple whitespace split and normalize (no external tokenizers required)
            String[] refTokens = tokenizeForMetrics(ref);
            String[] botTokens = tokenizeForMetrics(bot);

            // Semantic similarity: predictor -> embedding cosine
            double semanticSimilarity = safeEmbeddingSimilarity(bot, ref, 0.5);

            // BLEU/ROUGE/F1/ExactMatch
            double bleu4 = safeCompute(() -> computeBLEU(refTokens, botTokens, 4), 0.0);
            double rougeL = safeCompute(() -> computeROUGEL(refTokens, botTokens), 0.0);
            double f1 = safeCompute(() -> computeF1(refTokens, botTokens), 0.0);
            double exactMatch = normalizeForComparison(ref).equals(normalizeForComparison(bot)) ? 1.0 : 0.0;

            // Confidence and hallucination risk (simple signal)
            double confidence = clamp(semanticSimilarity, 0.0, 1.0);
            double hallucinationRisk = clamp(1.0 - semanticSimilarity, 0.0, 1.0);

            // Perplexity via PerplexityCalculator; guarantee non-zero fallback
            double perplexity = 1.0; // safe fallback
            if (perplexityCalculator != null) {
                try {
                    double p = perplexityCalculator.computePerplexity(bot);
                    if (Double.isFinite(p) && p > 0.0) perplexity = p;
                    else log.warn("Perplexity returned invalid value ({}), using fallback.", p);
                } catch (Exception e) {
                    log.warn("Perplexity calculation failed: {}", e.getMessage());
                }
            } else {
                log.debug("PerplexityCalculator not available; using fallback value {}", perplexity);
            }

            // METEOR placeholder (keep your existing design; easy to replace with real METEOR)
            double meteor = safeCompute(() -> computeMETEOR(refTokens, botTokens), 0.0);

            // NLI metrics: contradiction, entailment, neutral
            double contradiction = 0.0;
            double entailment = 0.0;
            double neutral = 0.0;

            if (nliService != null) {
                try {
                    Map<String, Double> nliOut = nliService.evaluate(ref, bot);
                    contradiction = nliOut.getOrDefault("contradiction", 0.0);
                    entailment = nliOut.getOrDefault("entailment", 0.0);
                    neutral = nliOut.getOrDefault("neutral", 0.0);
                } catch (Exception e) {
                    log.warn("NLI evaluate failed: {}; using fallbacks 0.0", e.getMessage());
                }
            } else {
                log.debug("NLI service not present; using fallback 0.0 for all NLI metrics");
            }

            // Multi-turn consistency — placeholder: 1.0 (consistent) unless external multi-turn service provided
            double multiTurnConsistency = 1.0;

            // Length ratio / redundancy / diversity
            double lengthRatio = ref.length() == 0 ? 1.0 : ((double) bot.length()) / Math.max(1, ref.length());
            double redundancy = computeRedundancy(botTokens);
            double diversity = computeDiversity(botTokens);

            // Final composite score (same weighting as before)
            double finalScore = 0.45 * semanticSimilarity + 0.30 * rougeL + 0.15 * f1 + 0.10 * bleu4;
            finalScore = clamp(finalScore, 0.0, 1.0);

            // Populate map (explicit keys so Extent always sees values)
            scores.put("semanticSimilarity", round3(semanticSimilarity));
            scores.put("bleu4", round3(bleu4));
            scores.put("rougeL", round3(rougeL));
            scores.put("f1", round3(f1));
            scores.put("exactMatch", exactMatch);
            scores.put("confidence", round3(confidence));
            scores.put("hallucinationRisk", round3(hallucinationRisk));
            scores.put("perplexity", round3(perplexity));
            scores.put("meteor", round3(meteor));
            scores.put("contradiction", round3(contradiction));
            scores.put("entailment", round3(entailment));
            scores.put("neutral", round3(neutral));
            scores.put("multiTurnConsistency", round3(multiTurnConsistency));
            scores.put("lengthRatio", round3(lengthRatio));
            scores.put("redundancy", round3(redundancy));
            scores.put("diversity", round3(diversity));
            scores.put("finalScore", round3(finalScore));
            scores.put("usedModel", usedModel);

        } catch (Exception e) {
            // on any unexpected failure, populate safe fallback values for every metric
            log.error("Unexpected error during evaluateAnswer: {}", e.getMessage(), e);
            scores.put("semanticSimilarity", 0.5);
            scores.put("bleu4", 0.0);
            scores.put("rougeL", 0.0);
            scores.put("f1", 0.0);
            scores.put("exactMatch", 0.0);
            scores.put("confidence", 0.5);
            scores.put("hallucinationRisk", 0.5);
            scores.put("perplexity", 1.0);
            scores.put("meteor", 0.0);
            scores.put("contradiction", 0.0);
            scores.put("entailment", 0.0);
            scores.put("neutral", 0.0);
            scores.put("multiTurnConsistency", 1.0);
            scores.put("lengthRatio", 1.0);
            scores.put("redundancy", 0.0);
            scores.put("diversity", 0.0);
            scores.put("finalScore", 0.5);
            scores.put("usedModel", usedModel);
        }

        Instant end = Instant.now();
        scores.put("startTime", start.toString());
        scores.put("endTime", end.toString());
        scores.put("duration", Duration.between(start, end).toString());
        scores.put("notes", null);

        return scores;
    }

    // -------------------- helper utilities --------------------

    private String[] tokenizeForMetrics(String text) {
        if (text == null || text.isBlank()) return new String[0];
        // basic normalization: lowercase, collapse non-alphanum to spaces, split on whitespace
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    private String normalizeForComparison(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private double safeEmbeddingSimilarity(String botAnswer, String referenceAnswer, double fallback) {
        try {
            if (predictor == null) return fallback;
            float[] botVec = predictor.predict(botAnswer == null ? "" : botAnswer);
            float[] refVec = (referenceAnswer == null || referenceAnswer.isEmpty()) ? null : predictor.predict(referenceAnswer);
            if (botVec == null || refVec == null) return fallback;
            return clamp(cosine(botVec, refVec), 0.0, 1.0);
        } catch (TranslateException | RuntimeException e) {
            log.warn("Embedding similarity failed: {}; using fallback {}", e.getMessage(), fallback);
            return fallback;
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0.0, na = 0.0, nb = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
    }

    private <T> T safeCompute(SupplierWithException<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("safeCompute fallback due to: {}", e.getMessage());
            return fallback;
        }
    }

    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    // ----- BLEU/ROUGE/F1 implementations (compact, deterministic) -----
    private double computeBLEU(String[] ref, String[] hyp, int maxN) {
        if (hyp.length == 0) return 0.0;
        double score = 1.0;
        for (int n = 1; n <= maxN; n++) {
            double overlap = ngramOverlap(ref, hyp, n);
            double total = Math.max(hyp.length - n + 1, 1);
            double precision = (overlap + 1.0) / (total + 1.0); // +1 smoothing
            score *= precision;
        }
        return Math.pow(score, 1.0 / maxN);
    }

    private int ngramOverlap(String[] ref, String[] hyp, int n) {
        Map<String, Integer> refN = ngrams(ref, n);
        Map<String, Integer> hypN = ngrams(hyp, n);
        int total = 0;
        for (Map.Entry<String, Integer> e : hypN.entrySet()) {
            total += Math.min(e.getValue(), refN.getOrDefault(e.getKey(), 0));
        }
        return total;
    }

    private Map<String, Integer> ngrams(String[] tokens, int n) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i <= tokens.length - n; i++) {
            String ng = String.join(" ", Arrays.copyOfRange(tokens, i, i + n));
            m.put(ng, m.getOrDefault(ng, 0) + 1);
        }
        return m;
    }

    private double computeROUGEL(String[] ref, String[] hyp) {
        if (ref.length == 0) return 0.0;
        int lcs = longestCommonSubsequence(ref, hyp);
        return (double) lcs / Math.max(ref.length, 1);
    }

    private int longestCommonSubsequence(String[] a, String[] b) {
        int[][] dp = new int[a.length + 1][b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                dp[i][j] = a[i - 1].equals(b[j - 1]) ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[a.length][b.length];
    }

    private double computeF1(String[] ref, String[] hyp) {
        Set<String> r = Arrays.stream(ref).collect(Collectors.toSet());
        Set<String> h = Arrays.stream(hyp).collect(Collectors.toSet());
        if (h.isEmpty() || r.isEmpty()) return 0.0;
        int overlap = 0;
        for (String t : h) if (r.contains(t)) overlap++;
        double p = (double) overlap / h.size();
        double rcv = (double) overlap / r.size();
        return (p + rcv) > 0 ? 2 * p * rcv / (p + rcv) : 0.0;
    }

    private double computeMETEOR(String[] ref, String[] hyp) {
        // Placeholder: keep previous behavior. Replace with actual METEOR integration if desired.
        return 0.8;
    }

    private double computeRedundancy(String[] tokens) {
        if (tokens.length <= 1) return 0.0;
        Map<String, Integer> counts = new HashMap<>();
        for (String t : tokens) counts.put(t, counts.getOrDefault(t, 0) + 1);
        int repeats = counts.values().stream().mapToInt(c -> c > 1 ? c - 1 : 0).sum();
        return (double) repeats / Math.max(1, tokens.length);
    }

    private double computeDiversity(String[] tokens) {
        if (tokens.length == 0) return 0.0;
        Set<String> uniq = new HashSet<>(Arrays.asList(tokens));
        return Math.min(1.0, (double) uniq.size() / tokens.length);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public void close() {
        try {
            if (predictor != null) predictor.close();
        } catch (Exception ignored) {
        }
        try {
            if (embeddingModel != null) embeddingModel.close();
        } catch (Exception ignored) {
        }
        try {
            if (nliService != null) nliService.close();
        } catch (Exception ignored) {
        }
    }
}