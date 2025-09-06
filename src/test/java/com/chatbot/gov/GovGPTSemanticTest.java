package com.chatbot.gov;

import com.aventstack.extentreports.MediaEntityBuilder;
import common.TestBase;
import org.testng.annotations.*;
import pages.GovGptPage;
import utils.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GovGPTSemanticTest extends TestBase {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SemanticComparer comparer;
    private GovGptPage chatPage;

    private final Duration TIMEOUT = Duration.ofSeconds(30);
    private final Duration POLLING = Duration.ofMillis(250);
    private final double THRESHOLD = 0.8;

    private List<TestCase> testCases;

    @BeforeClass
    public void init() throws Exception {
        // Initialize embedding model
        embeddingService = new EmbeddingService();
        embeddingService.init();

        // Setup in-memory vector store
        vectorStore = new InMemoryVectorStore();

        // Load test data from JSON
        testCases = JsonDataReader.read("testData.json");
        for (TestCase tc : testCases) {
            float[] refEmbedding = embeddingService.embed(tc.getReferenceAnswer());
            vectorStore.upsert(tc.getId(), refEmbedding, Map.of("referenceAnswer", tc.getReferenceAnswer()));
        }

        comparer = new SemanticComparer(embeddingService, vectorStore);

        // Initialize Chat Page
        chatPage = new GovGptPage(driver);
        chatPage.startNewChat();
    }

    @DataProvider(name = "chatbotData")
    public Object[][] chatbotData() {
        Object[][] data = new Object[testCases.size()][1];
        for (int i = 0; i < testCases.size(); i++) {
            data[i][0] = testCases.get(i);
        }
        return data;
    }

    @Test(dataProvider = "chatbotData")
    public void ChatbotSemantic(TestCase tc) throws Exception {
        // Send question to chatbot
        chatPage.enterBotRequest(tc.getQuestion());
        chatPage.btnSendPrompt();

        // Capture chatbot response
        String botAnswer = chatPage.BotResponse(TIMEOUT, POLLING);

        // Compare semantic similarity
        SemanticComparer.ComparisonResult result = comparer.compareToNearest(tc.getQuestion(), botAnswer, 5, THRESHOLD);

        // Log results
        String logMessage = "Question: " + tc.getQuestion() +
                "<br>Chatbot Answer: " + botAnswer +
                "<br>Best Matched Reference: " + result.matchedReference +
                "<br>Similarity: " + String.format("%.2f", result.similarity);

        if (result.pass) {
            test.pass("PASS | " + logMessage);
        } else {
            // Take screenshot only on failure
            String screenshotPath = captureScreenshot(driver, "FAIL_" + tc.getId());
            test.fail("FAIL | " + logMessage,
                    MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
        }

        System.out.println("Test Case: " + tc.getId() + " | PASS: " + result.pass + " | Similarity: " + result.similarity);
    }

    @AfterClass
    public void cleanup() {
        if (embeddingService != null) {
            embeddingService.close();
        }
    }
}