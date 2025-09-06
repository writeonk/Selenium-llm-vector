package utils;

import java.util.Map;

public class StubLLMService implements LLMService {

    @Override
    public Map<String, Object> evaluateAnswer(String question, String referenceAnswer, String botAnswer) {
        return Map.of(
                "accuracy", 0.9,
                "hallucination", false,
                "clarity", 0.85,
                "relevance", 0.95
        );
    }
}