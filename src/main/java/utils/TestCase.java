package utils;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {

    private String id;
    private String category;
    private String severity;
    private String severityAction;
    private String question;
    private String referenceAnswer;
    private List<String> expectedKeywords;
    private List<String> blacklist;
    private List<String> expectedBotResponseExamples;
    private String tone;
    private String format;
    private Constraints constraints;
    private Metadata metadata;
    private Scoring scoring;
    private LastRun lastRun;

    // Nested Classes
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Constraints {
        private String latencySeconds;
        private String language;

        // Getters/Setters
        public String getLatencySeconds() {
            return latencySeconds;
        }

        public void setLatencySeconds(String latencySeconds) {
            this.latencySeconds = latencySeconds;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String modelVersion;
        private double confidenceThreshold;
        private boolean sourceVerification;

        // Getters/Setters
        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public boolean isSourceVerification() {
            return sourceVerification;
        }

        public void setSourceVerification(boolean sourceVerification) {
            this.sourceVerification = sourceVerification;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Scoring {
        private int weight;
        private double passScore;
        private double warnScore;
        private double failScore;
        private String aggregation;

        // Getters/Setters
        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public double getPassScore() {
            return passScore;
        }

        public void setPassScore(double passScore) {
            this.passScore = passScore;
        }

        public double getWarnScore() {
            return warnScore;
        }

        public void setWarnScore(double warnScore) {
            this.warnScore = warnScore;
        }

        public double getFailScore() {
            return failScore;
        }

        public void setFailScore(double failScore) {
            this.failScore = failScore;
        }

        public String getAggregation() {
            return aggregation;
        }

        public void setAggregation(String aggregation) {
            this.aggregation = aggregation;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LastRun {
        private String status;
        private double score;
        private String runDate;
        private String evaluator;

        // Getters/Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getRunDate() {
            return runDate;
        }

        public void setRunDate(String runDate) {
            this.runDate = runDate;
        }

        public String getEvaluator() {
            return evaluator;
        }

        public void setEvaluator(String evaluator) {
            this.evaluator = evaluator;
        }
    }

    // Getters and Setters for all other fields
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSeverityAction() {
        return severityAction;
    }

    public void setSeverityAction(String severityAction) {
        this.severityAction = severityAction;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist;
    }

    public List<String> getExpectedBotResponseExamples() {
        return expectedBotResponseExamples;
    }

    public void setExpectedBotResponseExamples(List<String> expectedBotResponseExamples) {
        this.expectedBotResponseExamples = expectedBotResponseExamples;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Constraints getConstraints() {
        return constraints;
    }

    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public LastRun getLastRun() {
        return lastRun;
    }

    public void setLastRun(LastRun lastRun) {
        this.lastRun = lastRun;
    }
}