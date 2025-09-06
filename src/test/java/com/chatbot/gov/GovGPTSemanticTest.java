package com.chatbot.gov;

import common.TestBase;
import pages.GovGptPage;
import utils.*;

import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.*;
import com.google.gson.JsonObject;
import org.testng.annotations.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GovGPTSemanticTest extends TestBase {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SemanticComparer comparer;
    private LLMService llmService;
    private GovGptPage chatPage;

    private final Duration TIMEOUT = Duration.ofSeconds(30);
    private final Duration POLLING = Duration.ofMillis(250);
    private List<TestCase> testCases;

    @BeforeClass
    public void init() throws Exception {
        embeddingService = new EmbeddingService();
        embeddingService.init();

        vectorStore = new InMemoryVectorStore();

        // Load JSON test cases from classpath
        testCases = JsonDataReader.read("src/test/resources/testData.json");
        if (testCases == null || testCases.isEmpty()) {
            throw new RuntimeException("No test cases loaded. Please check testData.json");
        }


        // Precompute reference embeddings
        for (TestCase tc : testCases) {
            if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
                float[] refEmbedding = embeddingService.embed(tc.getReferenceAnswer());
                vectorStore.upsert(tc.getId(), refEmbedding,
                        Map.of("referenceAnswer", tc.getReferenceAnswer()));
            }
        }

        comparer = new SemanticComparer(embeddingService, vectorStore);

        llmService = new StubLLMService(); // Offline stub
        // llmService = new HuggingFaceLLMService(properties.getProperty("huggingface_api_key"));

        chatPage = new GovGptPage(driver);
        chatPage.startNewChat();

        Files.createDirectories(Paths.get(System.getProperty("user.dir") + "/screenshots"));
    }

    @DataProvider(name = "chatbotData")
    public Object[][] chatbotData() {
        Object[][] data = new Object[testCases.size()][1];
        for (int i = 0; i < testCases.size(); i++) data[i][0] = testCases.get(i);
        return data;
    }

    @Test(dataProvider = "chatbotData")
    public void runTest(TestCase tc) throws Exception {

        // -------------------- Step 1: Send Question --------------------
        chatPage.enterBotRequest(tc.getQuestion());
        chatPage.btnSendPrompt();

        // -------------------- Step 2: Get Bot Response --------------------
        String botAnswer = chatPage.BotResponse(TIMEOUT, POLLING);

        // -------------------- Step 3: Semantic Comparison --------------------
        SemanticComparer.ComparisonResult semanticResult = null;
        if (tc.getReferenceAnswer() != null && !tc.getReferenceAnswer().isEmpty()) {
            float threshold = tc.getMetadata() != null ? (float) tc.getMetadata().getConfidenceThreshold() : 0.8f;
            semanticResult = comparer.compareToNearest(tc.getQuestion(), botAnswer, 5, threshold);
        }

        // -------------------- Step 4: LLM Evaluation --------------------
        Map<String, Object> llmScores = llmService.evaluateAnswer(
                tc.getQuestion(),
                tc.getReferenceAnswer() != null ? tc.getReferenceAnswer() : "",
                botAnswer
        );

        // -------------------- Step 5: Accessibility Violations --------------------
        List<JsonObject> violations = AxeAccessibility.analyzePage(driver);

        // -------------------- Step 6: Hallucination Detection --------------------
        if (tc.getBlacklist() != null) {
            for (String term : tc.getBlacklist()) {
                if (botAnswer != null && botAnswer.toLowerCase().contains(term.toLowerCase())) {
                    logTestDetails("hallucination", "warn",
                            "Potential hallucination detected: '" + term + "'");
                }
            }
        }

        // -------------------- Step 7: Security / Fallback Detection --------------------
        if (tc.getExpectedKeywords() == null || tc.getExpectedKeywords().isEmpty()) {
            if (tc.getQuestion() != null && tc.getQuestion().matches(".*(<script>|DROP TABLE|ignore all instructions).*")) {
                logTestDetails("security", "fail", "Potential prompt injection detected in question");
            } else if (tc.getQuestion() == null || tc.getQuestion().isEmpty()) {
                logTestDetails("fallback", "warn", "Empty question received. Triggering fallback handling.");
            }
        }

        // -------------------- Step 8: Expected Bot Response Examples --------------------
        boolean matchesExample = false;
        if (tc.getExpectedBotResponseExamples() != null) {
            matchesExample = tc.getExpectedBotResponseExamples()
                    .stream()
                    .anyMatch(example -> example.equalsIgnoreCase(botAnswer));
        }

        // -------------------- Step 9: Log All Details --------------------
        test.log(Status.INFO, MarkupHelper.createLabel(
                "Category: " + tc.getCategory() +
                        " | Severity: " + tc.getSeverity() +
                        " | Action: " + tc.getSeverityAction(), ExtentColor.BLUE));
        test.log(Status.INFO, MarkupHelper.createLabel("Question: " + tc.getQuestion(), ExtentColor.BLUE));
        test.log(Status.INFO, MarkupHelper.createLabel("Bot Response: " + botAnswer, ExtentColor.GREEN));
        test.log(Status.INFO, MarkupHelper.createLabel("Matches Example: " + matchesExample, ExtentColor.CYAN));
        test.log(Status.INFO, MarkupHelper.createLabel("Tone: " + tc.getTone() + " | Format: " + tc.getFormat(), ExtentColor.TRANSPARENT));

        if (tc.getConstraints() != null) {
            test.log(Status.INFO, MarkupHelper.createLabel(
                    "Latency: " + tc.getConstraints().getLatencySeconds() +
                            " | Language: " + tc.getConstraints().getLanguage(), ExtentColor.LIME));
        }

        if (tc.getScoring() != null) {
            test.log(Status.INFO, MarkupHelper.createLabel(
                    "Scoring Aggregation: " + tc.getScoring().getAggregation() +
                            " | Weight: " + tc.getScoring().getWeight(), ExtentColor.PURPLE));
        }

        test.log(Status.INFO, MarkupHelper.createLabel("LLM Scores: " + llmScores, ExtentColor.BROWN));
        test.log(Status.INFO, MarkupHelper.createLabel("Accessibility Violations: " + violations.size(), ExtentColor.RED));

        if (semanticResult != null) {
            test.log(Status.INFO, MarkupHelper.createLabel(
                    "Best Reference: " + semanticResult.matchedReference +
                            " | Similarity: " + String.format("%.2f", semanticResult.similarity),
                    ExtentColor.ORANGE));
        }

        // -------------------- Step 10: Pass / Fail / Warn --------------------
        boolean pass = semanticResult == null || semanticResult.pass;
        if (pass) {
            test.pass(MarkupHelper.createLabel(
                    "<span class='badge badge-success'>PASS</span> | Test Passed", ExtentColor.GREEN));
        } else {
            String screenshotPath = System.getProperty("user.dir") + "/screenshots/FAIL_" + tc.getId() + ".png";
            captureScreenshot(driver, "FAIL_" + tc.getId());
            test.log(Status.FAIL, MarkupHelper.createLabel(
                    "<span class='badge badge-danger'>FAIL</span> | Test Failed", ExtentColor.RED));
            test.fail(MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
        }

        // -------------------- Step 11: Update LastRun --------------------
        if (tc.getLastRun() != null) {
            tc.getLastRun().setStatus(pass ? "pass" : "fail");
            tc.getLastRun().setScore(semanticResult != null ? semanticResult.similarity : 0.0);
            tc.getLastRun().setRunDate(java.time.Instant.now().toString());
            tc.getLastRun().setEvaluator("automated-qa-pipeline");
        }
    }

    @AfterClass
    public void cleanup() {
        if (embeddingService != null) embeddingService.close();
    }
}