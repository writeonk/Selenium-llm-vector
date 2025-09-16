package utils;

import java.util.*;

/**
 * Advanced Stub LLM for offline GovGPT testing.
 * Simulates realistic LLM metrics: semantic similarity, hallucination, BLEU/ROUGE/F1,
 * multi-turn drift, perplexity variation, and confidence.
 */
public class StubLLMService implements LLMService {

    private final Random random = new Random(42); // deterministic for repeatable tests
    private final Map<String, String> conversationMemory = new HashMap<>();

    @Override
    public Map<String, Object> evaluateAnswer(
            String question,
            String referenceAnswer,
            String botAnswer,
            String previousBotAnswer,
            String userContext,
            List<String> blacklistTerms) {

        Map<String, Object> scores = new HashMap<>();

        // Normalize text
        String ref = referenceAnswer != null ? referenceAnswer.toLowerCase().trim() : "";
        String bot = botAnswer != null ? botAnswer.toLowerCase().trim() : "";

        // ------------------- Handle empty botAnswer -------------------
        if (bot.isEmpty()) {
            scores.put("semanticSimilarity", 0.0);
            scores.put("bleu4", 0.0);
            scores.put("rougeL", 0.0);
            scores.put("f1", 0.0);
            scores.put("exactMatch", 0.0);
            scores.put("confidence", 0.0);
            scores.put("hallucinationRisk", 1.0);
            scores.put("finalScore", 0.0);
            scores.put("usedModel", "STUB_ADVANCED");
            scores.put("perplexity", 2.0 + random.nextDouble()); // slightly higher
            scores.put("multiTurnConsistency", 0.0);
            scores.put("redundancy", 0.2);
            scores.put("diversity", 0.5);
            scores.put("lengthRatio", 0.0);
            return scores;
        }

        // ------------------- Semantic similarity -------------------
        double semanticSim = computeSemanticSimilarity(ref, bot);
        semanticSim = clamp(semanticSim + randomNoise(0.05), 0.0, 1.0);

        // ------------------- BLEU/ROUGE/F1 -------------------
        String[] refTokens = ref.split("\\s+");
        String[] botTokens = bot.split("\\s+");
        double bleu4 = clamp(computeBLEU(refTokens, botTokens, 4) + randomNoise(0.05), 0.0, 1.0);
        double rougeL = clamp(computeROUGEL(refTokens, botTokens) + randomNoise(0.05), 0.0, 1.0);
        double f1 = clamp(computeF1(refTokens, botTokens) + randomNoise(0.05), 0.0, 1.0);
        boolean exactMatch = ref.equals(bot);

        // ------------------- Confidence & Hallucination -------------------
        double confidence = clamp(semanticSim + 0.1 * randomNoise(0.1), 0.0, 1.0);
        double hallucinationRisk = clamp(1.0 - semanticSim + 0.1 * randomNoise(0.1), 0.0, 1.0);

        // ------------------- Multi-turn consistency -------------------
        double multiTurnConsistency = 1.0;
        if (previousBotAnswer != null && !previousBotAnswer.isEmpty()) {
            multiTurnConsistency = computeMultiTurnConsistency(previousBotAnswer, bot);
        }

        // ------------------- Length & diversity -------------------
        double lengthRatio = ref.isEmpty() ? 1.0 : (double) bot.length() / ref.length();
        double redundancy = countRepeatedTokens(bot);
        double diversity = typeTokenRatio(bot);

        // ------------------- Perplexity (simulated) -------------------
        double perplexity = clamp(0.8 + random.nextDouble() * 1.5, 0.0, 3.0);

        // ------------------- Composite final score -------------------
        double finalScore = 0.45 * semanticSim + 0.3 * rougeL + 0.15 * f1 + 0.1 * bleu4;

        // ------------------- Store conversation -------------------
        conversationMemory.put(question, bot);

        // ------------------- Blacklist hallucinations -------------------
        List<String> detectedHallucinations = new ArrayList<>();
        boolean blacklistHit = false;
        if (blacklistTerms != null) {
            for (String term : blacklistTerms) {
                if (bot.contains(term.toLowerCase())) {
                    blacklistHit = true;
                    detectedHallucinations.add(term);
                }
            }
        }

        // ------------------- Save all scores -------------------
        scores.put("semanticSimilarity", semanticSim);
        scores.put("bleu4", bleu4);
        scores.put("rougeL", rougeL);
        scores.put("f1", f1);
        scores.put("exactMatch", exactMatch ? 1.0 : 0.0);
        scores.put("confidence", confidence);
        scores.put("hallucinationRisk", hallucinationRisk);
        scores.put("finalScore", finalScore);
        scores.put("usedModel", "STUB_ADVANCED");
        scores.put("perplexity", perplexity);
        scores.put("multiTurnConsistency", multiTurnConsistency);
        scores.put("lengthRatio", lengthRatio);
        scores.put("redundancy", redundancy);
        scores.put("diversity", diversity);
        scores.put("blacklistHit", blacklistHit ? 1.0 : 0.0);
        scores.put("detectedHallucinations", detectedHallucinations);

        return scores;
    }

    // ------------------- Helper methods -------------------

    private double computeSemanticSimilarity(String ref, String bot) {
        if (ref.isEmpty()) return 0.5;
        Set<String> refWords = new HashSet<>(Arrays.asList(ref.split("\\s+")));
        Set<String> botWords = new HashSet<>(Arrays.asList(bot.split("\\s+")));
        long overlap = botWords.stream().filter(refWords::contains).count();
        return (double) overlap / Math.max(refWords.size(), 1);
    }

    private double computeBLEU(String[] ref, String[] hyp, int maxN) {
        double score = 1.0;
        for (int n = 1; n <= maxN; n++) {
            double overlap = ngramOverlap(ref, hyp, n);
            double total = Math.max(hyp.length - n + 1, 1);
            double precision = (overlap + 1.0) / (total + 1.0); // smoothed
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
        return (double) lcs / Math.max(ref.length, 1);
    }

    private int longestCommonSubsequence(String[] a, String[] b) {
        int[][] dp = new int[a.length + 1][b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                if (a[i - 1].equals(b[j - 1])) dp[i][j] = dp[i - 1][j - 1] + 1;
                else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[a.length][b.length];
    }

    private double computeF1(String[] ref, String[] hyp) {
        Set<String> refSet = new HashSet<>(Arrays.asList(ref));
        Set<String> hypSet = new HashSet<>(Arrays.asList(hyp));
        int overlap = 0;
        for (String token : hypSet) if (refSet.contains(token)) overlap++;
        double precision = (double) overlap / Math.max(hypSet.size(), 1);
        double recall = (double) overlap / Math.max(refSet.size(), 1);
        return (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
    }

    private double computeMultiTurnConsistency(String prev, String current) {
        String[] prevTokens = prev.split("\\s+");
        String[] currTokens = current.split("\\s+");
        long overlap = Arrays.stream(currTokens).filter(s -> Arrays.asList(prevTokens).contains(s)).count();
        return clamp((double) overlap / Math.max(prevTokens.length, 1), 0.0, 1.0);
    }

    private double countRepeatedTokens(String text) {
        String[] tokens = text.split("\\s+");
        Map<String, Integer> counts = new HashMap<>();
        for (String t : tokens) counts.put(t, counts.getOrDefault(t, 0) + 1);
        int repeats = counts.values().stream().mapToInt(c -> c > 1 ? c - 1 : 0).sum();
        return (double) repeats / tokens.length;
    }

    private double typeTokenRatio(String text) {
        String[] tokens = text.split("\\s+");
        Set<String> types = new HashSet<>(Arrays.asList(tokens));
        return (double) types.size() / Math.max(tokens.length, 1);
    }

    private double randomNoise(double range) {
        return (random.nextDouble() * 2 - 1) * range;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public void clearMemory() {
        conversationMemory.clear();
    }
}