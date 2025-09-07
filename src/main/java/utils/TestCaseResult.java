package utils;

import java.util.List;

public class TestCaseResult {
    public String testCaseId;
    public String botAnswer;
    public double semanticScore;
    public double llmConfidence;
    public double exampleScore;   // new field
    public double finalScore;
    public boolean pass;
    public boolean warn;
    public boolean blacklistHit;
    public List<String> hallucinationTerms;

    public TestCaseResult(String testCaseId, String botAnswer, double semanticScore, double llmConfidence,
                          double exampleScore, double finalScore, boolean pass, boolean warn,
                          boolean blacklistHit, List<String> hallucinationTerms) {
        this.testCaseId = testCaseId;
        this.botAnswer = botAnswer;
        this.semanticScore = semanticScore;
        this.llmConfidence = llmConfidence;
        this.exampleScore = exampleScore; // assign it
        this.finalScore = finalScore;
        this.pass = pass;
        this.warn = warn;
        this.blacklistHit = blacklistHit;
        this.hallucinationTerms = hallucinationTerms;
    }
}