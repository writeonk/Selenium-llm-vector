package utils;

import java.util.Map;

/**
 * LLMService interface defines methods for evaluating chatbot responses dynamically.
 * Allows multiple implementations: stub, HuggingFace, OpenAI, or enterprise LLMs.
 */
public interface LLMService {

    /**
     * Evaluate bot response against question and optional reference answer.
     * Returns scores/metrics, e.g., accuracy, relevance, completeness, hallucination risk.
     *
     * @param question       User question
     * @param referenceAnswer Optional reference answer
     * @param botAnswer      Bot response
     * @return Map of metricName → score/object
     */
    Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer);

}