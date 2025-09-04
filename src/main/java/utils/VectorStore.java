package utils;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(String id, float[] vector, Map<String, Object> payload);

    List<ScoredReference> search(float[] query, int k);

    class ScoredReference {
        public final String id;
        public final double score;
        public final String referenceAnswer;

        public ScoredReference(String id, double score, String referenceAnswer) {
            this.id = id;
            this.score = score;
            this.referenceAnswer = referenceAnswer;
        }
    }
}
