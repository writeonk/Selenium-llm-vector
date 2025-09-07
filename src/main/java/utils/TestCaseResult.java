package utils;

import java.util.List;
import java.util.Map;

public class TestCaseResult {
    public String testCaseId;
    public String botAnswer;

    // Existing fields (kept)
    public double semanticScore;
    public double llmConfidence;   // <-- Confidence (kept)
    public double exampleScore;
    public double finalScore;
    public boolean pass;
    public boolean warn;
    public boolean blacklistHit;
    public List<String> hallucinationTerms;

    // New NLP metric fields
    public double bleu4;
    public double rougeL;
    public double f1Score;
    public double exactMatch;
    public double compositeScore;
    public String usedModel;

    // Optional extensible map
    public Map<String, Double> extraScores;

    public TestCaseResult(String testCaseId, String botAnswer,
                          double semanticScore, double llmConfidence, double exampleScore,
                          double finalScore, boolean pass, boolean warn, boolean blacklistHit,
                          List<String> hallucinationTerms,
                          double bleu4, double rougeL, double f1Score, double exactMatch,
                          double compositeScore, String usedModel) {
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
    }
}