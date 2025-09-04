# GovGPT Chatbot Automation Suite

## Overview
This project provides an **automated testing framework** for government-related chatbots, combining **App validation with semantic similarity checks** using **vector embeddings**.

The framework leverages:

- **Selenium WebDriver**: Automates chatbot UI interactions.
- **DJL (Deep Java Library) + PyTorch**: Generates embeddings for semantic comparison.
- **In-memory vector store**: Stores reference Q&A embeddings for fast similarity checks.
- **TestNG**: Executes tests dynamically.
- **ExtentReports**: Logs results, failures, and generates HTML & JSON reports.

The goal is to ensure the chatbot returns **accurate, semantically correct answers** for government queries.

---

## Features

1. **Dynamic Test Execution**
    - Reads test cases from `testData.json`.
    - Executes each test case as a separate TestNG test dynamically.
    - Supports 100+ test cases for scalability.

2. **Semantic Validation**
    - Embeds reference answers and chatbot responses.
    - Compares similarity using cosine similarity.
    - Pass/fail determined by configurable threshold (default: 0.8).

3. **UI Automation**
    - Supports Chrome, Firefox, and Safari.
    - Interacts with chatbot input, send button, and response elements.
    - Handles asynchronous responses using `FluentWait`.

4. **Reporting**
    - ExtentReports integration.
    - Screenshots captured **only on failures**.
    - Logs question, chatbot response, best matched reference, and similarity score.
    - Generates both HTML and JSON reports.

5. **Scalable Design**
    - Modular services: `EmbeddingService`, `InMemoryVectorStore`, `SemanticComparer`.
    - JSON-driven test data for easy extension.

---

## Project Structure

/src/main/java
├── pages/
│   └── GovGptPage.java             # Page Object for chatbot UI
├── utils/
│   ├── EmbeddingService.java       # Embedding generation via DJL + PyTorch
│   ├── InMemoryVectorStore.java    # Stores embeddings for reference answers
│   ├── SemanticComparer.java       # Compares semantic similarity
│   ├── VectorStore.java            # Interface for vector storage
│   ├── TestCase.java               # Test case POJO
│   ├── JsonDataReader.java         # Reads test cases from JSON
│   └── VectorUtils.java            # Cosine similarity utility
└── common/
└── TestBase.java               # Base class for WebDriver setup, reports, screenshots

/src/test/resources
└── testData.json                   # JSON file containing Q&A test cases

---

## Prerequisites

- Java 17+
- Gradle 8+
- Browsers: Chrome, Firefox
---

## Dependencies

- Selenium Java 4.35.0
- WebDriverManager 6.2.0
- DJL 0.34.0 (API, tokenizers, PyTorch engine)
- TestNG 7.10.2
- ExtentReports 5.1.2
- Jackson 2.15.2
- Apache Commons, Log4j, Jsoup

---

## Configuration
 `src/test/resources/config.properties`

## Running test
`./gradlew test `

## Test Data
-	Test cases are loaded from testData.json.
-	Each test case executes as a separate TestNG test via a data provider.

## Extent Spark Reports
-	HTML report: reports/semantic-report_<timestamp>.html
-	Screenshots captured only on failures in screenshots/.

## Keynotes
-	EmbeddingService uses ai.djl.huggingface:tokenizers
-	GovGPTSemanticTest runs all JSON test cases dynamically.
-	Screenshots and logs are captured only on failures.
-	Framework is designed for scalability and reusability.