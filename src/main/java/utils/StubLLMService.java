package utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight LLM stub for offline or testing purposes.
 * Returns deterministic or mock scores.
 */
public class StubLLMService implements LLMService {

    @Override
    public Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer) {
        Map<String, Object> scores = new HashMap<>();

        if (botAnswer == null || botAnswer.isEmpty()) {
            scores.put("relevance", 0.0);
            scores.put("hallucinationRisk", 1.0);
            scores.put("confidence", 0.0);
        } else if (referenceAnswer != null && !referenceAnswer.isEmpty()) {
            // Simple word-overlap metric for demo
            double overlap = computeWordOverlap(referenceAnswer, botAnswer);
            scores.put("relevance", overlap);
            scores.put("hallucinationRisk", 1.0 - overlap);
            scores.put("confidence", overlap);
        } else {
            // No reference answer scenario
            scores.put("relevance", 0.5);
            scores.put("hallucinationRisk", 0.5);
            scores.put("confidence", 0.5);
        }

        return scores;
    }

    private double computeWordOverlap(String ref, String bot) {
        String[] refWords = ref.toLowerCase().split("\\W+");
        String[] botWords = bot.toLowerCase().split("\\W+");
        long count = 0;
        for (String w : refWords) {
            for (String bw : botWords) {
                if (w.equals(bw)) count++;
            }
        }
        return (double) count / refWords.length;
    }
}