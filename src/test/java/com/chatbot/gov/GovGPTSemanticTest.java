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
    private final Duration TIMEOUT = Duration.ofSeconds(30);
    private final Duration POLLING = Duration.ofMillis(250);
    private final List<TestCaseResult> allResults = new ArrayList<>();

    // <-- Switch between LLM modes here -->

    // private final LLMMode llmMode = LLMMode.STUB;          // Uses dummy/mock service
    private final LLMMode llmMode = LLMMode.HUGGINGFACE;  // Uses real HuggingFace model
    private final String huggingFaceModel = "sentence-transformers/all-MiniLM-L6-v2";

    @BeforeClass
    public void init() throws Exception {
        // Initialize embedding service and vector store
        embeddingService = new EmbeddingService();
        embeddingService.init();
        vectorStore = new InMemoryVectorStore();

        // Load test cases from JSON
        InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("testData1.json");
        if (jsonStream == null) throw new RuntimeException("Cannot find testData1.json!");
        testCases = JsonDataReader.read(jsonStream);
        if (testCases == null || testCases.isEmpty()) throw new RuntimeException("No test cases loaded!");

        // Populate vector store with reference embeddings
        for (TestCase tc : testCases) {
            if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
                float[] refEmbedding = embeddingService.embed(tc.getReferenceAnswer());
                vectorStore.upsert(tc.getId(), refEmbedding, Map.of("referenceAnswer", tc.getReferenceAnswer()));
            }
        }

        // Initialize semantic comparer
        comparer = new SemanticComparer(embeddingService, vectorStore);

        // <-- Simplified switchable LLM -->
        llmService = switch (llmMode) {
            case STUB -> new StubLLMService();
            case HUGGINGFACE -> new HuggingFaceLLMService(huggingFaceModel); // Inject backbone model name
        };

        // Initialize chat page
        chatPage = new GovGptPage(driver);
        chatPage.startNewChat();

        // Ensure directories exist
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

        // Add LLM mode info
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

        // LLM evaluation (switchable)
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

        // Notes for prompt injection / fallback
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

        // Scoring
        double semanticScore = semanticResult != null ? semanticResult.similarity : 0.0;
        double llmConfidence = llmScores.getOrDefault("confidence", 0.0) instanceof Number ?
                ((Number) llmScores.get("confidence")).doubleValue() : 0.0;
        double exampleScore = matchesExample ? 1.0 : 0.0;
        double finalScore = (semanticScore + llmConfidence + exampleScore) / 3.0;

        boolean pass = finalScore >= 0.8;
        boolean warn = !pass && finalScore >= 0.5;

        LocalDateTime endTime = LocalDateTime.now();

        // Logging
        logMetadataTable(tc, testCaseExtent);
        logQATable(tc, botAnswer, matchesExample, testCaseExtent);
        logLLMTable(llmScores, semanticScore, testCaseExtent);
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

        allResults.add(new TestCaseResult(
                tc.getId(),
                botAnswer,
                semanticScore,
                llmConfidence,
                exampleScore,
                finalScore,
                pass,
                warn,
                blacklistHit,
                detectedHallucinations
        ));
    }

    @AfterClass
    public void cleanup() throws Exception {
        if (embeddingService != null) embeddingService.close();
        if (llmService instanceof HuggingFaceLLMService) ((HuggingFaceLLMService) llmService).close();
        generateSummaryDashboard(allResults);
    }

    // ---------------- Helper Methods ----------------
    private void logMetadataTable(TestCase tc, ExtentTest testCaseExtent) {
        StringBuilder metaTable = new StringBuilder();
        metaTable.append("<b>Test Metadata</b><br>")
                .append("<table style='border-collapse:collapse;color:black;border:2px solid black;'>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>ID</td><td style='border:2px solid black;padding:4px;'>").append(tc.getId()).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Category</td><td style='border:2px solid black;padding:4px;'>").append(tc.getCategory()).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Severity</td><td style='border:2px solid black;padding:4px;'>").append(tc.getSeverity()).append("</td></tr>")
                .append("</table><br>");
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(metaTable.toString(), ExtentColor.TRANSPARENT));
    }

    private void logQATable(TestCase tc, String botAnswer, boolean matchesExample, ExtentTest testCaseExtent) {
        StringBuilder qaTable = new StringBuilder();
        qaTable.append("<b>Question & Bot Response</b><br>")
                .append("<table style='border-collapse:collapse;color:black;border:2px solid black;'>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Question Asked</td><td style='border:2px solid black;padding:4px;' title='").append(tc.getQuestion()).append("'>").append(abbreviate(tc.getQuestion(), 150)).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Reference Answer</td><td style='border:2px solid black;padding:4px;' title='").append(tc.getReferenceAnswer()).append("'>").append(abbreviate(tc.getReferenceAnswer(), 150)).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Bot Response</td><td style='border:2px solid black;padding:4px;' title='").append(botAnswer).append("'>").append(abbreviate(botAnswer, 150)).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Matches Expected Examples</td><td style='border:2px solid black;padding:4px;'>").append(matchesExample ? "<span style='color:green;'>YES</span>" : "<span style='color:red;'>NO</span>").append("</td></tr>")
                .append("</table><br>");
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(qaTable.toString(), ExtentColor.TRANSPARENT));
    }

    private void logLLMTable(Map<String, Object> llmScores, double semanticScore, ExtentTest testCaseExtent) {
        StringBuilder llmTable = new StringBuilder();
        llmTable.append("<b>LLM Scores</b><br>")
                .append("<table style='border-collapse:collapse;color:black;border:2px solid black;'>")
                .append(formatScoreRow("Hallucination Risk", llmScores.getOrDefault("hallucinationRisk", 0.0)))
                .append(formatScoreRow("Confidence", llmScores.getOrDefault("confidence", 0.0)))
                .append(formatScoreRow("Relevance", llmScores.getOrDefault("relevance", 0.0)))
                .append("</table><br>");
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(llmTable.toString(), ExtentColor.TRANSPARENT));
    }

    private void logOtherEvaluations(TestCase tc, LocalDateTime startTime, LocalDateTime endTime,
                                     double semanticScore, double finalScore, boolean blacklistHit, String notes,
                                     List<JsonObject> accessibilityViolations, List<String> detectedHallucinations,
                                     ExtentTest testCaseExtent) {
        StringBuilder evalTable = new StringBuilder();
        evalTable.append("<b>Other Evaluations</b><br>")
                .append("<table style='border-collapse:collapse;color:black;border:2px solid black;'>")
                .append(formatScoreRow("Semantic Similarity", semanticScore))
                .append("<tr><td style='border:2px solid black;padding:4px;'>Accessibility Violations</td><td style='border:2px solid black;padding:4px;'>").append(accessibilityViolations.size()).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Blacklist Hit</td><td style='border:2px solid black;padding:4px;'>").append(blacklistHit ? "<span style='color:red;'>YES</span>" : "NO").append("</td></tr>")
                .append(formatScoreRow("Final Score", finalScore))
                .append("<tr><td style='border:2px solid black;padding:4px;'>Start Time</td><td style='border:2px solid black;padding:4px;'>").append(startTime).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>End Time</td><td style='border:2px solid black;padding:4px;'>").append(endTime).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Duration</td><td style='border:2px solid black;padding:4px;'>").append(Duration.between(startTime, endTime)).append("</td></tr>")
                .append("<tr><td style='border:2px solid black;padding:4px;'>Notes</td><td style='border:2px solid black;padding:4px;'>").append(notes).append("</td></tr>")
                .append("</table><br>");
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(evalTable.toString(), ExtentColor.TRANSPARENT));
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String formatScoreRow(String name, Object value) {
        double val = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        String color = val >= 0.8 ? "green" : val >= 0.5 ? "orange" : "red";
        return "<tr><td style='border:2px solid black;padding:4px;'>" + name + "</td><td style='border:2px solid black;padding:4px;color:" + color + ";'>" + String.format("%.3f", val) + "</td></tr>";
    }

    // ---------------- Summary Dashboard ----------------
    public void generateSummaryDashboard(List<TestCaseResult> results) throws Exception {
        int total = results.size();
        long passCount = results.stream().filter(r -> r.pass).count();
        long warnCount = results.stream().filter(r -> r.warn).count();
        long failCount = total - passCount - warnCount;

        double avgFinalScore = results.stream().mapToDouble(r -> r.finalScore).average().orElse(0.0);
        double avgSemantic = results.stream().mapToDouble(r -> r.semanticScore).average().orElse(0.0);
        double avgLLMConfidence = results.stream().mapToDouble(r -> r.llmConfidence).average().orElse(0.0);

        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Chatbot Test Dashboard</title>")
                .append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script></head><body>")
                .append("<h1>Chatbot Test Summary</h1>")
                .append("<p>Total Tests: ").append(total).append("</p>")
                .append("<p>PASS: ").append(passCount).append(" | WARN: ").append(warnCount)
                .append(" | FAIL: ").append(failCount).append("</p>")
                .append("<p>Average Final Score: ").append(String.format("%.3f", avgFinalScore))
                .append(" | Semantic: ").append(String.format("%.3f", avgSemantic))
                .append(" | LLM Confidence: ").append(String.format("%.3f", avgLLMConfidence))
                .append("</p>")
                // Optional: add pie chart for visual summary
                .append("<canvas id='summaryChart' width='400' height='200'></canvas>")
                .append("<script>")
                .append("var ctx = document.getElementById('summaryChart').getContext('2d');")
                .append("new Chart(ctx, {type: 'pie', data: {labels: ['PASS','WARN','FAIL'], datasets: [{data: [")
                .append(passCount).append(",").append(warnCount).append(",").append(failCount)
                .append("], backgroundColor: ['#28a745','#ffc107','#dc3545']]}});")
                .append("</script>")
                .append("</body></html>");

        Files.writeString(Paths.get(System.getProperty("user.dir") + "/reports/SummaryDashboard.html"), html.toString());
    }
}