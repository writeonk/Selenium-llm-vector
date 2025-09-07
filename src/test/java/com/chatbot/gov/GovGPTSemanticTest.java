package com.chatbot.gov;

import common.TestBase;
import pages.GovGptPage;
import utils.*;

import com.aventstack.extentreports.*;
import com.aventstack.extentreports.markuputils.*;
import com.google.gson.JsonObject;
import org.testng.annotations.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class GovGPTSemanticTest extends TestBase {

    public enum LLMMode {STUB, HUGGINGFACE}

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SemanticComparer comparer;
    private LLMService llmService;
    private GovGptPage chatPage;
    private List<TestCase> testCases;
    private final Duration TIMEOUT = Duration.ofSeconds(75);
    private final Duration POLLING = Duration.ofMillis(250);
    private final List<TestCaseResult> allResults = new ArrayList<>();

    // Switch LLM mode here
    // private final LLMMode llmMode = LLMMode.STUB;
    private final LLMMode llmMode = LLMMode.HUGGINGFACE;
    private final String huggingFaceModel = "sentence-transformers/all-MiniLM-L6-v2";

    @BeforeClass
    public void init() throws Exception {
        embeddingService = new EmbeddingService();
        embeddingService.init();
        vectorStore = new InMemoryVectorStore();

        // Load test cases
        InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("testData1.json");
        if (jsonStream == null) throw new RuntimeException("Cannot find testData1.json!");
        testCases = JsonDataReader.read(jsonStream);
        if (testCases == null || testCases.isEmpty())
            throw new RuntimeException("No test cases loaded!");

        // Populate vector store
        for (TestCase tc : testCases) {
            if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
                float[] refEmbedding = embeddingService.embed(tc.getReferenceAnswer());
                vectorStore.upsert(tc.getId(), refEmbedding, Map.of("referenceAnswer", tc.getReferenceAnswer()));
            }
        }

        comparer = new SemanticComparer(embeddingService, vectorStore);

        // LLM mode
        llmService = switch (llmMode) {
            case STUB -> new StubLLMService();
            case HUGGINGFACE -> new HuggingFaceLLMService(huggingFaceModel);
        };

        chatPage = new GovGptPage(driver);
        chatPage.startNewChat();

        Files.createDirectories(Paths.get(System.getProperty("user.dir") + "/screenshots"));
        Files.createDirectories(Paths.get(System.getProperty("user.dir") + "/reports"));
    }

    @Test
    public void executeAllTests() throws Exception {
        for (TestCase tc : testCases) {
            runTest(tc);
        }
    }

    private void runTest(TestCase tc) {
        LocalDateTime startTime = LocalDateTime.now();

        String testName = String.format(
                "ID: %s | Category: %s | Severity: %s | Question: %s",
                tc.getId(), tc.getCategory(), tc.getSeverity(), abbreviate(tc.getQuestion(), 150)
        );
        ExtentTest testCaseExtent = extent.createTest(testName);

        String llmInfo = llmMode == LLMMode.HUGGINGFACE
                ? "LLM Mode: " + llmMode + " | Model: " + huggingFaceModel
                : "LLM Mode: " + llmMode;
        testCaseExtent.info(MarkupHelper.createLabel(llmInfo, ExtentColor.BLUE));

        // Bot interaction
        chatPage.enterBotRequest(tc.getQuestion());
        chatPage.btnSendPrompt();
        String botAnswer = chatPage.BotResponse(TIMEOUT, POLLING);

        // Semantic comparison
        SemanticComparer.ComparisonResult semanticResult = null;
        if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
            semanticResult = comparer.compareToNearest(tc.getQuestion(), botAnswer, 5, 0.8);
        }

        // LLM evaluation
        Map<String, Object> llmScores = llmService.evaluateAnswer(
                tc.getQuestion(),
                tc.getReferenceAnswer() != null ? tc.getReferenceAnswer() : "",
                botAnswer
        );

        // Accessibility & blacklist
        List<JsonObject> accessibilityViolations = AxeAccessibility.analyzePage(driver);
        boolean blacklistHit = false;
        List<String> detectedHallucinations = new ArrayList<>();
        if (tc.getBlacklist() != null) {
            for (String term : tc.getBlacklist()) {
                if (botAnswer != null && botAnswer.toLowerCase().contains(term.toLowerCase())) {
                    blacklistHit = true;
                    detectedHallucinations.add(term);
                    testCaseExtent.log(Status.WARNING,
                            MarkupHelper.createLabel("Hallucination | Detected: '" + term + "'", ExtentColor.ORANGE));
                }
            }
        }

        // Notes
        String notes = "";
        if (tc.getExpectedKeywords() == null || tc.getExpectedKeywords().isEmpty()) {
            if (tc.getQuestion() != null && tc.getQuestion().matches(".*(<script>|DROP TABLE|ignore all instructions).*")) {
                notes += "Potential prompt injection; ";
                testCaseExtent.log(Status.FAIL, "Security | Prompt injection detected");
            } else if (tc.getQuestion() == null || tc.getQuestion().isEmpty()) {
                notes += "Empty question received; fallback triggered; ";
                testCaseExtent.log(Status.WARNING, "Fallback | Empty question received.");
            }
        }

        boolean matchesExample = tc.getExpectedBotResponseExamples() != null &&
                tc.getExpectedBotResponseExamples().stream()
                        .anyMatch(example -> example.equalsIgnoreCase(botAnswer));

        // Scores
        double semanticScore = semanticResult != null ? semanticResult.similarity : 0.0;
        double llmConfidence = getDouble(llmScores, "confidence");
        double exampleScore = matchesExample ? 1.0 : 0.0;
        double finalScore = (semanticScore + llmConfidence + exampleScore) / 3.0;

        boolean pass = finalScore >= 0.8;
        boolean warn = !pass && finalScore >= 0.5;

        LocalDateTime endTime = LocalDateTime.now();

        // Logs
        logMetadataTable(tc, testCaseExtent);
        logQATable(tc, botAnswer, matchesExample, testCaseExtent);
        logLLMTable(llmScores, testCaseExtent);
        logOtherEvaluations(tc, startTime, endTime, semanticScore, finalScore, blacklistHit, notes,
                accessibilityViolations, detectedHallucinations, testCaseExtent);

        if (pass)
            testCaseExtent.pass(MarkupHelper.createLabel("<span class='badge badge-success'>PASS</span>", ExtentColor.GREEN));
        else if (warn)
            testCaseExtent.warning(MarkupHelper.createLabel("<span class='badge badge-warning'>WARN</span>", ExtentColor.ORANGE));
        else {
            String screenshotPath = System.getProperty("user.dir") + "/screenshots/FAIL_" + tc.getId() + ".png";
            captureScreenshot(driver, "FAIL_" + tc.getId());
            testCaseExtent.fail(MarkupHelper.createLabel("<span class='badge badge-danger'>FAIL</span>", ExtentColor.RED));
            testCaseExtent.fail(MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
        }

        // Persist result
        TestCaseResult result = new TestCaseResult(
                tc.getId(),
                botAnswer,
                semanticScore,
                llmConfidence,
                exampleScore,
                finalScore,
                pass,
                warn,
                blacklistHit,
                detectedHallucinations,
                getDouble(llmScores, "bleu4"),
                getDouble(llmScores, "rougeL"),
                getDouble(llmScores, "f1"),
                getDouble(llmScores, "exactMatch"),
                getDouble(llmScores, "finalScore"),
                String.valueOf(llmScores.getOrDefault("usedModel", "unknown"))
        );
        allResults.add(result);
    }

    @AfterClass
    public void cleanup() throws Exception {
        if (embeddingService != null) embeddingService.close();
        if (llmService instanceof HuggingFaceLLMService) ((HuggingFaceLLMService) llmService).close();
        generateSummaryDashboard(allResults);
    }

    // ---------------- Helpers ----------------
    private double getDouble(Map<String, Object> map, String key) {
        return map.getOrDefault(key, 0.0) instanceof Number
                ? ((Number) map.get(key)).doubleValue() : 0.0;
    }

    private void logMetadataTable(TestCase tc, ExtentTest testCaseExtent) {
        String html = "<b>Test Metadata</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                "<tr><td>ID</td><td>" + tc.getId() + "</td></tr>" +
                "<tr><td>Category</td><td>" + tc.getCategory() + "</td></tr>" +
                "<tr><td>Severity</td><td>" + tc.getSeverity() + "</td></tr>" +
                "</table><br>";
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    private void logQATable(TestCase tc, String botAnswer, boolean matchesExample, ExtentTest testCaseExtent) {
        String html = "<b>Question & Bot Response</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                "<tr><td>Question Asked</td><td title='" + tc.getQuestion() + "'>" + abbreviate(tc.getQuestion(), 150) + "</td></tr>" +
                "<tr><td>Reference Answer</td><td title='" + tc.getReferenceAnswer() + "'>" + abbreviate(tc.getReferenceAnswer(), 150) + "</td></tr>" +
                "<tr><td>Bot Response</td><td title='" + botAnswer + "'>" + abbreviate(botAnswer, 150) + "</td></tr>" +
                "<tr><td>Matches Expected Examples</td><td>" + (matchesExample ? "<span style='color:green;'>YES</span>" : "<span style='color:red;'>NO</span>") + "</td></tr>" +
                "</table><br>";
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    private void logLLMTable(Map<String, Object> llmScores, ExtentTest testCaseExtent) {
        String html = "<b>LLM Scores</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                formatScoreRow("Semantic Similarity", llmScores.getOrDefault("semanticSimilarity", 0.0)) +
                formatScoreRow("BLEU-4 (smoothed)", llmScores.getOrDefault("bleu4", 0.0)) +
                formatScoreRow("ROUGE-L", llmScores.getOrDefault("rougeL", 0.0)) +
                formatScoreRow("F1 Score", llmScores.getOrDefault("f1", 0.0)) +
                formatScoreRow("Exact Match", llmScores.getOrDefault("exactMatch", 0.0)) +
                formatScoreRow("Confidence", llmScores.getOrDefault("confidence", 0.0)) +
                formatScoreRow("Hallucination Risk", llmScores.getOrDefault("hallucinationRisk", 0.0)) +
                formatScoreRow("Final Composite Score", llmScores.getOrDefault("finalScore", 0.0)) +
                "<tr><td>Model Used</td><td>" + llmScores.getOrDefault("usedModel", "N/A") + "</td></tr>" +
                "</table><br>";
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    private void logOtherEvaluations(TestCase tc, LocalDateTime startTime, LocalDateTime endTime,
                                     double semanticScore, double finalScore, boolean blacklistHit, String notes,
                                     List<JsonObject> accessibilityViolations, List<String> detectedHallucinations,
                                     ExtentTest testCaseExtent) {
        String html = "<b>Other Evaluations</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                "<tr><td>Accessibility Violations</td><td>" + accessibilityViolations.size() + "</td></tr>" +
                "<tr><td>Blacklist Hit</td><td>" + (blacklistHit ? "<span style='color:red;'>YES</span>" : "NO") + "</td></tr>" +
                "<tr><td>Start Time</td><td>" + startTime + "</td></tr>" +
                "<tr><td>End Time</td><td>" + endTime + "</td></tr>" +
                "<tr><td>Duration</td><td>" + Duration.between(startTime, endTime) + "</td></tr>" +
                "<tr><td>Notes</td><td>" + notes + "</td></tr>" +
                "</table><br>";
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String formatScoreRow(String name, Object value) {
        double val = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        String color = val >= 0.8 ? "green" : val >= 0.5 ? "orange" : "red";
        return "<tr><td>" + name + "</td><td style='color:" + color + ";'>" + String.format("%.3f", val) + "</td></tr>";
    }

    // ---------------- Dashboard ----------------
    public void generateSummaryDashboard(List<TestCaseResult> results) throws Exception {
        int total = results.size();
        long passCount = results.stream().filter(r -> r.pass).count();
        long warnCount = results.stream().filter(r -> r.warn).count();
        long failCount = total - passCount - warnCount;

        double avgFinalScore = results.stream().mapToDouble(r -> r.finalScore).average().orElse(0.0);
        double avgSemantic = results.stream().mapToDouble(r -> r.semanticScore).average().orElse(0.0);
        double avgLLMConfidence = results.stream().mapToDouble(r -> r.llmConfidence).average().orElse(0.0);
        double avgBleu = results.stream().mapToDouble(r -> r.bleu4).average().orElse(0.0);
        double avgRouge = results.stream().mapToDouble(r -> r.rougeL).average().orElse(0.0);
        double avgF1 = results.stream().mapToDouble(r -> r.f1Score).average().orElse(0.0);
        double avgComposite = results.stream().mapToDouble(r -> r.compositeScore).average().orElse(0.0);

        String html = "<html><head><title>Chatbot Test Dashboard</title>" +
                "<script src='https://cdn.jsdelivr.net/npm/chart.js'></script></head><body>" +
                "<h1>Chatbot Test Summary</h1>" +
                "<p>Total Tests: " + total + "</p>" +
                "<p>PASS: " + passCount + " | WARN: " + warnCount + " | FAIL: " + failCount + "</p>" +
                "<h3>Average Scores</h3><ul>" +
                "<li>Final Score: " + String.format("%.3f", avgFinalScore) + "</li>" +
                "<li>Semantic Similarity: " + String.format("%.3f", avgSemantic) + "</li>" +
                "<li>LLM Confidence: " + String.format("%.3f", avgLLMConfidence) + "</li>" +
                "<li>BLEU-4: " + String.format("%.3f", avgBleu) + "</li>" +
                "<li>ROUGE-L: " + String.format("%.3f", avgRouge) + "</li>" +
                "<li>F1 Score: " + String.format("%.3f", avgF1) + "</li>" +
                "<li>Composite Score: " + String.format("%.3f", avgComposite) + "</li></ul>" +

                "<canvas id='summaryChart' width='400' height='200'></canvas>" +
                "<script>new Chart(document.getElementById('summaryChart'), {type: 'pie', data: {labels: ['PASS','WARN','FAIL'], datasets: [{data: [" +
                passCount + "," + warnCount + "," + failCount +
                "], backgroundColor: ['#28a745','#ffc107','#dc3545']} ]}});</script>" +

                "<canvas id='metricsChart' width='600' height='300'></canvas>" +
                "<script>new Chart(document.getElementById('metricsChart'), {type: 'bar', data: {" +
                "labels: ['Semantic','BLEU-4','ROUGE-L','F1','Confidence','Composite']," +
                "datasets: [{label: 'Average Scores', data: [" +
                String.format("%.3f", avgSemantic) + "," +
                String.format("%.3f", avgBleu) + "," +
                String.format("%.3f", avgRouge) + "," +
                String.format("%.3f", avgF1) + "," +
                String.format("%.3f", avgLLMConfidence) + "," +
                String.format("%.3f", avgComposite) +
                "], backgroundColor: '#007bff'}]}});</script>" +
                "</body></html>";

        Files.writeString(Paths.get(System.getProperty("user.dir") + "/reports/SummaryDashboard.html"), html);
    }
}