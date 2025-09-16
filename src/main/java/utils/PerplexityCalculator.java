package utils;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.util.Map;

public class PerplexityCalculator {

    private final String modelId;

    /**
     * Constructor allows switching models easily (e.g., "gpt2" or "distilgpt2").
     */
    public PerplexityCalculator(String modelId) {
        this.modelId = modelId != null ? modelId : "gpt2";
    }

    /**
     * Computes perplexity of the given text using the Hugging Face GPT model via DJL.
     */
    public double computePerplexity(String text) throws IOException, ModelException, TranslateException {
        Criteria<String, Float> criteria = Criteria.builder()
                .optEngine("PyTorch")
                .setTypes(String.class, Float.class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId)
                .optArguments(Map.of("task", "perplexity"))
                .build();

        try (ZooModel<String, Float> model = ModelZoo.loadModel(criteria);
             Predictor<String, Float> predictor = model.newPredictor()) {
            return predictor.predict(text);
        }
    }

    public static void main(String[] args) {
        try {
            PerplexityCalculator pc = new PerplexityCalculator("gpt2");
            String input = "ChatGPT is very good at generating human-like text.";
            double perplexity = pc.computePerplexity(input);
            System.out.println("Perplexity = " + perplexity);
        } catch (IOException | ModelException | TranslateException e) {
            e.printStackTrace();
        }
    }
}