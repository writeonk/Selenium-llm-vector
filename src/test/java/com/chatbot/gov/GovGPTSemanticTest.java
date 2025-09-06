package com.chatbot.gov;

import common.TestBase;
import pages.GovGptPage;
import utils.*;

import com.aventstack.extentreports.MediaEntityBuilder;
import com.google.gson.JsonObject;
import org.testng.annotations.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GovGPTSemanticTest extends TestBase {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SemanticComparer comparer;
    private LLMService llmService;         // Pluggable LLM service
    private GovGptPage chatPage;

    private final Duration TIMEOUT = Duration.ofSeconds(30);
    private final Duration POLLING = Duration.ofMillis(250);
    private final double THRESHOLD = 0.8;
    private List<TestCase> testCases;

    @BeforeClass
    public void init() throws Exception {
        // Initialize embeddings
        embeddingService = new EmbeddingService();
        embeddingService.init();

        // Initialize vector store
        vectorStore = new InMemoryVectorStore();

        // Load test cases from JSON
        testCases = JsonDataReader.read("testData.json");
        for (TestCase tc : testCases) {
            float[] refEmbedding = embeddingService.embed(tc.getReferenceAnswer());
            vectorStore.upsert(tc.getId(), refEmbedding, Map.of("referenceAnswer", tc.getReferenceAnswer()));
        }

        comparer = new SemanticComparer(embeddingService, vectorStore);

        // Initialize LLM service (choose one)
        llmService = new StubLLMService(); // offline
        // llmService = new HuggingFaceLLMService(properties.getProperty("huggingface_api_key")); // online

        // Initialize chatbot page
        chatPage = new GovGptPage(driver);
        chatPage.startNewChat();
    }

    @DataProvider(name = "chatbotData")
    public Object[][] chatbotData() {
        Object[][] data = new Object[testCases.size()][1];
        for (int i = 0; i < testCases.size(); i++) data[i][0] = testCases.get(i);
        return data;
    }

    @Test(dataProvider = "chatbotData")
    public void testChatbotAnswers(TestCase tc) throws Exception {
        // Send question to chatbot
        chatPage.enterBotRequest(tc.getQuestion());
        chatPage.btnSendPrompt();

        // Capture bot response
        String botAnswer = chatPage.BotResponse(TIMEOUT, POLLING);

        // Semantic comparison
        SemanticComparer.ComparisonResult semanticResult =
                comparer.compareToNearest(tc.getQuestion(), botAnswer, 5, THRESHOLD);

        // LLM evaluation
        Map<String, Object> llmScores =
                llmService.evaluateAnswer(tc.getQuestion(), tc.getReferenceAnswer(), botAnswer);

        // Accessibility check
        List<JsonObject> violations = AxeAccessibility.analyzePage(driver);

        // Log results
        String logMessage = "Question: " + tc.getQuestion() +
                "<br>Chatbot Answer: " + botAnswer +
                "<br>Best Reference: " + semanticResult.matchedReference +
                "<br>Similarity: " + String.format("%.2f", semanticResult.similarity) +
                "<br>LLM Scores: " + llmScores +
                "<br>Accessibility Violations: " + violations.size();

        if (semanticResult.pass) {
            test.pass("PASS | " + logMessage);
        } else {
            String screenshotPath = captureScreenshot(driver, "FAIL_" + tc.getId());
            test.fail("FAIL | " + logMessage,
                    MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
        }

        System.out.println("Test Case: " + tc.getId() +
                " | PASS: " + semanticResult.pass +
                " | Similarity: " + semanticResult.similarity +
                " | LLM Scores: " + llmScores);
    }

    @AfterClass
    public void cleanup() {
        if (embeddingService != null) embeddingService.close();
    }
}