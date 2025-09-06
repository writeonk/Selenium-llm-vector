package utils;

import java.util.Map;

public interface LLMService {
    /**
     * Evaluate the bot answer against the reference answer.
     *
     * @param question        user question
     * @param referenceAnswer expected answer
     * @param botAnswer       chatbot generated answer
     * @return Map with scores: accuracy, hallucination, clarity, relevance
     */
    Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer);
}