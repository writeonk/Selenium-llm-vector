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
import java.util.stream.Collectors;

public class GovGPTSemanticTest extends TestBase {

    public enum LLMMode {STUB, HUGGINGFACE}

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SemanticComparer comparer;
    private LLMService llmService;
    private GovGptPage chatPage;
    private PerplexityCalculator perplexityCalculator;
    private MeteorCalculator meteorCalculator;
    private NLIService nliService;
    private MultiTurnService multiTurnService;
    private List<TestCase> testCases;
    private final Duration TIMEOUT = Duration.ofSeconds(75);
    private final Duration POLLING = Duration.ofMillis(250);
    private final List<TestCaseResult> allResults = new ArrayList<>();
    private final Map<String, String> conversationHistory = new HashMap<>();

    private final LLMMode llmMode = LLMMode.HUGGINGFACE;
    private final String huggingFaceModel = "sentence-transformers/all-MiniLM-L6-v2";

    @BeforeClass
    public void init() throws Exception {
        embeddingService = new EmbeddingService();
        embeddingService.init();
        vectorStore = new InMemoryVectorStore();

        perplexityCalculator = new PerplexityCalculator("gpt2");
        meteorCalculator = new MeteorCalculator();
        nliService = new NLIService();
        multiTurnService = new MultiTurnService();

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
    public void executeAllTests() {
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

        // ---- Bot Interaction ----
        chatPage.enterBotRequest(tc.getQuestion());
        chatPage.btnSendPrompt();
        String botAnswer = chatPage.BotResponse(TIMEOUT, POLLING);

        // ---- Semantic Comparison ----
        SemanticComparer.ComparisonResult semanticResult = null;
        if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
            semanticResult = comparer.compareToNearest(tc.getQuestion(), botAnswer, 5, 0.8);
        }

        // ---- LLM Evaluation (6 parameters) ----
        String previousBotAnswer = conversationHistory.getOrDefault(tc.getId(), "");
        String userContext = ""; // Can be replaced with real context
        List<String> extraContext = tc.getExpectedKeywords() != null ? tc.getExpectedKeywords() : List.of();

        Map<String, Object> llmScores = llmService.evaluateAnswer(
                tc.getQuestion(),
                tc.getReferenceAnswer() != null ? tc.getReferenceAnswer() : "",
                botAnswer,
                "testUser",            // userId placeholder
                "session123",          // sessionId placeholder
                extraContext
        );

        conversationHistory.put(tc.getId(), botAnswer);

        // ---- Additional Metrics ----
        try {
            llmScores.put("perplexity", perplexityCalculator.computePerplexity(botAnswer));
        } catch (Exception e) {
            llmScores.put("perplexity", 0.0);
        }

        double meteorScore = tc.getReferenceAnswer() != null ? meteorCalculator.computeScore(tc.getReferenceAnswer(), botAnswer) : 0.0;
        llmScores.put("meteor", meteorScore);

        double contradiction = tc.getReferenceAnswer() != null && nliService.isContradiction(tc.getReferenceAnswer(), botAnswer) ? 1.0 : 0.0;
        llmScores.put("contradiction", contradiction);

        double multiTurnConsistency = previousBotAnswer.isEmpty() ? 1.0 : (multiTurnService.isConsistent(previousBotAnswer, botAnswer) ? 1.0 : 0.0);
        llmScores.put("multiTurnConsistency", multiTurnConsistency);

        double lengthRatio = tc.getReferenceAnswer() != null ? ((double) botAnswer.length() / tc.getReferenceAnswer().length()) : 1.0;
        double redundancy = multiTurnService.countRepeatedTokens(botAnswer);
        double diversity = multiTurnService.typeTokenRatio(botAnswer);
        llmScores.put("lengthRatio", lengthRatio);
        llmScores.put("redundancy", redundancy);
        llmScores.put("diversity", diversity);

        // ---- Accessibility & Blacklist ----
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

        // ---- Notes & Security ----
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

        // ---- Compute Scores ----
        double semanticScore = semanticResult != null ? semanticResult.similarity : 0.0;
        double llmConfidence = getDouble(llmScores, "confidence");
        double exampleScore = matchesExample ? 1.0 : 0.0;
        double finalScore = (semanticScore + llmConfidence + exampleScore) / 3.0;

        boolean pass = finalScore >= 0.8;
        boolean warn = !pass && finalScore >= 0.5;

        LocalDateTime endTime = LocalDateTime.now();

        // ---- Logging ----
        logMetadataTable(tc, testCaseExtent);
        logQATable(tc, botAnswer, matchesExample, testCaseExtent);
        logLLMTable(llmScores, testCaseExtent);
        logOtherEvaluations(tc, startTime, endTime, semanticScore, finalScore, blacklistHit, notes,
                accessibilityViolations, detectedHallucinations, testCaseExtent);

        // ---- Test Result Status ----
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

        // ---- Persist Result ----
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
                String.valueOf(llmScores.getOrDefault("usedModel", "unknown")),
                getDouble(llmScores, "perplexity"),
                getDouble(llmScores, "meteor"),
                getDouble(llmScores, "contradiction"),
                getDouble(llmScores, "multiTurnConsistency"),
                getDouble(llmScores, "lengthRatio"),
                getDouble(llmScores, "redundancy"),
                getDouble(llmScores, "diversity")
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

    private String abbreviate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String formatScoreRow(String name, Object value) {
        double val = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        String color = val >= 0.8 ? "green" : val >= 0.5 ? "orange" : "red";
        return "<tr><td>" + name + "</td><td style='color:" + color + ";'>" + String.format("%.3f", val) + "</td></tr>";
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
        String html = "<b>LLM Scores & Additional Metrics</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                formatScoreRow("Semantic Similarity", llmScores.getOrDefault("semanticSimilarity", 0.0)) +
                formatScoreRow("BLEU-4 (smoothed)", llmScores.getOrDefault("bleu4", 0.0)) +
                formatScoreRow("ROUGE-L", llmScores.getOrDefault("rougeL", 0.0)) +
                formatScoreRow("F1 Score", llmScores.getOrDefault("f1", 0.0)) +
                formatScoreRow("Exact Match", llmScores.getOrDefault("exactMatch", 0.0)) +
                formatScoreRow("Confidence", llmScores.getOrDefault("confidence", 0.0)) +
                formatScoreRow("Perplexity", llmScores.getOrDefault("perplexity", 0.0)) +
                formatScoreRow("METEOR", llmScores.getOrDefault("meteor", 0.0)) +
                formatScoreRow("Contradiction", llmScores.getOrDefault("contradiction", 0.0)) +
                formatScoreRow("Multi-turn Consistency", llmScores.getOrDefault("multiTurnConsistency", 0.0)) +
                formatScoreRow("Length Ratio", llmScores.getOrDefault("lengthRatio", 0.0)) +
                formatScoreRow("Redundancy", llmScores.getOrDefault("redundancy", 0.0)) +
                formatScoreRow("Diversity", llmScores.getOrDefault("diversity", 0.0)) +
                formatScoreRow("Final Composite Score", llmScores.getOrDefault("finalScore", 0.0)) +
                "<tr><td>Model Used</td><td>" + llmScores.getOrDefault("usedModel", "N/A") + "</td></tr>" +
                "</table><br>";
        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    private void logOtherEvaluations(TestCase tc, LocalDateTime startTime, LocalDateTime endTime,
                                     double semanticScore, double finalScore, boolean blacklistHit, String notes,
                                     List<JsonObject> accessibilityViolations, List<String> detectedHallucinations,
                                     ExtentTest testCaseExtent) {

        StringBuilder hallucinationsHtml = new StringBuilder();
        if (detectedHallucinations != null && !detectedHallucinations.isEmpty()) {
            for (String term : detectedHallucinations) {
                hallucinationsHtml.append(term).append(", ");
            }
            // Remove trailing comma
            hallucinationsHtml.setLength(hallucinationsHtml.length() - 2);
        } else {
            hallucinationsHtml.append("None");
        }

        String html = "<b>Other Evaluations</b><br>" +
                "<table style='border-collapse:collapse;color:black;border:2px solid black;'>" +
                "<tr><td>Accessibility Violations</td><td>" + accessibilityViolations.size() + "</td></tr>" +
                "<tr><td>Blacklist Hit</td><td>" + (blacklistHit ? "<span style='color:red;'>YES</span>" : "NO") + "</td></tr>" +
                "<tr><td>Detected Hallucinations</td><td>" + hallucinationsHtml + "</td></tr>" +
                "<tr><td>Semantic Score</td><td>" + String.format("%.3f", semanticScore) + "</td></tr>" +
                "<tr><td>Final Score</td><td>" + String.format("%.3f", finalScore) + "</td></tr>" +
                "<tr><td>Start Time</td><td>" + startTime + "</td></tr>" +
                "<tr><td>End Time</td><td>" + endTime + "</td></tr>" +
                "<tr><td>Duration</td><td>" + Duration.between(startTime, endTime) + "</td></tr>" +
                "<tr><td>Notes</td><td>" + (notes.isEmpty() ? "None" : notes) + "</td></tr>" +
                "</table><br>";

        testCaseExtent.log(Status.INFO, MarkupHelper.createLabel(html, ExtentColor.TRANSPARENT));
    }

    public void generateSummaryDashboard(List<TestCaseResult> results) throws Exception {
        int total = results.size();
        long passCount = results.stream().filter(r -> r.pass).count();
        long warnCount = results.stream().filter(r -> r.warn).count();
        long failCount = total - passCount - warnCount;

        // Average metrics
        double avgSemantic = results.stream().mapToDouble(r -> r.semanticScore).average().orElse(0.0);
        double avgLLMConfidence = results.stream().mapToDouble(r -> r.llmConfidence).average().orElse(0.0);
        double avgFinalScore = results.stream().mapToDouble(r -> r.finalScore).average().orElse(0.0);

        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Chatbot Test Dashboard</title>")
                .append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>")
                .append("<script src='https://code.jquery.com/jquery-3.7.1.min.js'></script>")
                .append("<link rel='stylesheet' href='https://cdn.datatables.net/1.13.6/css/jquery.dataTables.min.css'/>")
                .append("<script src='https://cdn.datatables.net/1.13.6/js/jquery.dataTables.min.js'></script>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:20px;}") // clean font
                .append("h1,h2{color:#333;}") // header style
                .append(".green{color:#28a745}.orange{color:#ffc107}.red{color:#dc3545}") // color codes
                .append("table, th, td {border:1px solid black; border-collapse:collapse; padding:5px; text-align:center;}")
                .append("th{background-color:#f0f0f0;}")
                .append("</style>")
                .append("</head><body>")
                .append("<h1>Chatbot QA Test Dashboard</h1>")
                .append("<p>Total Tests: ").append(total).append("</p>")
                .append("<p><b>PASS:</b> ").append(passCount)
                .append(" | <b>WARN:</b> ").append(warnCount)
                .append(" | <b>FAIL:</b> ").append(failCount).append("</p>")

                // Charts
                .append("<div style='width:45%; display:inline-block;'><canvas id='summaryChart'></canvas></div>")
                .append("<div style='width:50%; display:inline-block;'><canvas id='avgMetricsChart'></canvas></div>")
                .append("<div style='width:95%; margin-top:30px;'><canvas id='perTestMetricsChart'></canvas></div>")

                // Detailed table
                .append("<h2>Detailed Test Results</h2>")
                .append("<table id='resultsTable'><thead>")
                .append("<tr><th>ID</th><th>Bot Answer</th><th>Semantic</th><th>Confidence</th><th>Final Score</th><th>Status</th></tr></thead><tbody>");

        for (TestCaseResult r : results) {
            String status = r.pass ? "PASS" : r.warn ? "WARN" : "FAIL";
            html.append("<tr>")
                    .append("<td>").append(r.testCaseId).append("</td>")
                    .append("<td title='").append(r.botAnswer.replace("'", "&apos;")).append("'>").append(abbreviate(r.botAnswer, 50)).append("</td>")
                    .append("<td class='").append(r.semanticScore >= 0.8 ? "green" : r.semanticScore >= 0.5 ? "orange" : "red").append("'>").append(String.format("%.3f", r.semanticScore)).append("</td>")
                    .append("<td class='").append(r.llmConfidence >= 0.8 ? "green" : r.llmConfidence >= 0.5 ? "orange" : "red").append("'>").append(String.format("%.3f", r.llmConfidence)).append("</td>")
                    .append("<td>").append(String.format("%.3f", r.finalScore)).append("</td>")
                    .append("<td>").append(status).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table>")
                .append("<script>")
                // PASS/WARN/FAIL pie chart
                .append("new Chart(document.getElementById('summaryChart'), {type:'pie', data:{labels:['PASS','WARN','FAIL'], datasets:[{data:[").append(passCount).append(",").append(warnCount).append(",").append(failCount).append("], backgroundColor:['#28a745','#ffc107','#dc3545']}]}, options:{plugins:{title:{display:true,text:'Overall Test Status'}}}});")

                // Average metrics bar chart
                .append("new Chart(document.getElementById('avgMetricsChart'), {type:'bar', data:{labels:['Semantic','Confidence','Final'], datasets:[{label:'Average Scores', data:[").append(String.format("%.3f", avgSemantic)).append(",").append(String.format("%.3f", avgLLMConfidence)).append(",").append(String.format("%.3f", avgFinalScore)).append("], backgroundColor:['#007bff','#17a2b8','#ffc107']}]}, options:{plugins:{title:{display:true,text:'Average Metrics'}}}});")

                // Per-test metrics stacked bar chart
                .append("const perTestLabels=[").append(results.stream().map(r -> "'" + r.testCaseId + "'").collect(Collectors.joining(","))).append("];")
                .append("const semanticData=[").append(results.stream().map(r -> String.format("%.3f", r.semanticScore)).collect(Collectors.joining(","))).append("];")
                .append("const confidenceData=[").append(results.stream().map(r -> String.format("%.3f", r.llmConfidence)).collect(Collectors.joining(","))).append("];")
                .append("const finalData=[").append(results.stream().map(r -> String.format("%.3f", r.finalScore)).collect(Collectors.joining(","))).append("];")
                .append("new Chart(document.getElementById('perTestMetricsChart'), {type:'bar', data:{labels:perTestLabels, datasets:[{label:'Semantic', data:semanticData, backgroundColor:'#28a745'},{label:'Confidence', data:confidenceData, backgroundColor:'#17a2b8'},{label:'Final', data:finalData, backgroundColor:'#ffc107'}]}, options:{plugins:{title:{display:true,text:'Per-Test Metrics Comparison'}}, responsive:true, scales:{y:{beginAtZero:true, max:1}}}});")

                // DataTable initialization
                .append("$(document).ready(function(){$('#resultsTable').DataTable({pageLength:10, scrollX:true});});")
                .append("</script></body></html>");

        Files.writeString(Paths.get(System.getProperty("user.dir") + "/reports/SummaryDashboard.html"), html.toString());
    }
}