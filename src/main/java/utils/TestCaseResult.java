package utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCaseResult {
    public String testCaseId;
    public String botAnswer;

    // Existing fields
    public double semanticScore;
    public double llmConfidence;
    public double exampleScore;
    public double finalScore;
    public boolean pass;
    public boolean warn;
    public boolean blacklistHit;
    public List<String> hallucinationTerms;

    // NLP metric fields
    public double bleu4;
    public double rougeL;
    public double f1Score;
    public double exactMatch;
    public double compositeScore;
    public String usedModel;

    // Extended evaluation metrics
    public double perplexity;
    public double meteor;
    public double contradiction;
    public double multiTurnConsistency;
    public double lengthRatio;
    public double redundancy;
    public double diversity;

    // Optional extensible map for future metrics
    public Map<String, Double> extraScores;

    // Full constructor
    public TestCaseResult(String testCaseId, String botAnswer,
                          double semanticScore, double llmConfidence, double exampleScore,
                          double finalScore, boolean pass, boolean warn, boolean blacklistHit,
                          List<String> hallucinationTerms,
                          double bleu4, double rougeL, double f1Score, double exactMatch,
                          double compositeScore, String usedModel,
                          double perplexity, double meteor, double contradiction,
                          double multiTurnConsistency, double lengthRatio,
                          double redundancy, double diversity) {
        this.testCaseId = testCaseId;
        this.botAnswer = botAnswer;
        this.semanticScore = semanticScore;
        this.llmConfidence = llmConfidence;
        this.exampleScore = exampleScore;
        this.finalScore = finalScore;
        this.pass = pass;
        this.warn = warn;
        this.blacklistHit = blacklistHit;
        this.hallucinationTerms = hallucinationTerms;
        this.bleu4 = bleu4;
        this.rougeL = rougeL;
        this.f1Score = f1Score;
        this.exactMatch = exactMatch;
        this.compositeScore = compositeScore;
        this.usedModel = usedModel;
        this.perplexity = perplexity;
        this.meteor = meteor;
        this.contradiction = contradiction;
        this.multiTurnConsistency = multiTurnConsistency;
        this.lengthRatio = lengthRatio;
        this.redundancy = redundancy;
        this.diversity = diversity;

        // Automatically populate extraScores map
        populateExtraScores();
    }

    // Constructor with extraScores map
    public TestCaseResult(String testCaseId, String botAnswer,
                          double semanticScore, double llmConfidence, double exampleScore,
                          double finalScore, boolean pass, boolean warn, boolean blacklistHit,
                          List<String> hallucinationTerms,
                          double bleu4, double rougeL, double f1Score, double exactMatch,
                          double compositeScore, String usedModel,
                          Map<String, Double> extraScores) {
        this.testCaseId = testCaseId;
        this.botAnswer = botAnswer;
        this.semanticScore = semanticScore;
        this.llmConfidence = llmConfidence;
        this.exampleScore = exampleScore;
        this.finalScore = finalScore;
        this.pass = pass;
        this.warn = warn;
        this.blacklistHit = blacklistHit;
        this.hallucinationTerms = hallucinationTerms;
        this.bleu4 = bleu4;
        this.rougeL = rougeL;
        this.f1Score = f1Score;
        this.exactMatch = exactMatch;
        this.compositeScore = compositeScore;
        this.usedModel = usedModel;
        this.extraScores = extraScores;
    }

    private void populateExtraScores() {
        extraScores = new HashMap<>();
        extraScores.put("semanticScore", semanticScore);
        extraScores.put("llmConfidence", llmConfidence);
        extraScores.put("exampleScore", exampleScore);
        extraScores.put("finalScore", finalScore);
        extraScores.put("bleu4", bleu4);
        extraScores.put("rougeL", rougeL);
        extraScores.put("f1Score", f1Score);
        extraScores.put("exactMatch", exactMatch);
        extraScores.put("compositeScore", compositeScore);
        extraScores.put("perplexity", perplexity);
        extraScores.put("meteor", meteor);
        extraScores.put("contradiction", contradiction);
        extraScores.put("multiTurnConsistency", multiTurnConsistency);
        extraScores.put("lengthRatio", lengthRatio);
        extraScores.put("redundancy", redundancy);
        extraScores.put("diversity", diversity);
    }
}