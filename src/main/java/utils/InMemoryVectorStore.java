package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryVectorStore implements VectorStore {

    private static final Logger logger = LogManager.getLogger(InMemoryVectorStore.class);

    private final Map<String, float[]> vectors = new ConcurrentHashMap<>();
    private final Map<String, String> referenceMap = new ConcurrentHashMap<>();

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> payload) {
        vectors.put(id, vector);
        referenceMap.put(id, (String) payload.getOrDefault("referenceAnswer", payload.get("question")));
        logger.info("Vector upserted for id: {}", id);
    }

    @Override
    public List<VectorStore.ScoredReference> search(float[] query, int k) {
        List<VectorStore.ScoredReference> results = vectors.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), EmbeddingService.cosine(e.getValue(), query)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> new VectorStore.ScoredReference(e.getKey(), e.getValue(), referenceMap.get(e.getKey())))
                .collect(Collectors.toList());

        logger.info("Search returned {} results.", results.size());
        return results;
    }
}