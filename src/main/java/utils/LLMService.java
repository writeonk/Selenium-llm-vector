package utils;

import java.util.List;
import java.util.Map;

/**
 * LLMService interface defines methods for evaluating chatbot responses dynamically.
 */
public interface LLMService {

    /**
     * Evaluate bot response against question and optional reference answer.
     *
     * @param question        User question
     * @param referenceAnswer Optional reference answer
     * @param botAnswer       Bot response
     * @param userId          Optional user identifier
     * @param sessionId       Optional session identifier
     * @param extraContext    Optional extra context for evaluation
     * @return Map of metricName → score/object
     */
    Map<String, Object> evaluateAnswer(
            String question,
            String referenceAnswer,
            String botAnswer,
            String userId,
            String sessionId,
            List<String> extraContext
    );
}