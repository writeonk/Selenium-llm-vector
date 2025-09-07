# GovGPT Chatbot Automation Suite

## Overview
This project provides an **automated testing framework** for government-related chatbots, combining **App validation with semantic similarity checks** using **vector embeddings**.

The framework leverages:

- **Selenium WebDriver**: Automates chatbot UI interactions.
- **DJL (Deep Java Library) + PyTorch**: Generates embeddings for semantic comparison.
- **HuggingFace Models**: for semantic embeddings.
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
    - Uses HuggingFace model
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
    - Modular services: `EmbeddingService`, `HuggingFaceLLMService`, `InMemoryVectorStore`, `SemanticComparer`.
    - JSON-driven test data for easy extension.

---

## Evaluation & Scoring

This project implements a world-class LLM evaluation framework, incorporating multiple layers of automated and semantic checks:

1. **Semantic Similarity**
   - Embedding-based similarity between bot answer and reference answer.
   - Provides confidence and hallucination risk scores.

2. **Lexical Metrics**
   - BLEU-4 (smoothed), ROUGE-L, and F1 Score.
   - Exact Match is also tracked.
   - Tokenization handled via Apache OpenNLP.

3. **Composite Scoring**
   - Weighted combination of semantic similarity and lexical metrics.
   - Produces a single “Final Composite Score” per test case.

4. **Fallback & Model Tracking**
   - Multiple verified HuggingFace embedding models supported.
   - Logs which model was actually used for each test.

5. **Blacklist & Safety Checks**
   - Detects predefined sensitive terms (hallucinations).
   - Provides warning labels in the report for potential unsafe content.

6. **Accessibility & Other Checks**
   - Tracks accessibility violations using Axe accessibility analyzer.
   - Notes potential prompt injections or empty question fallbacks.

7. **Detailed Reporting**
   - Generates per-test HTML reports including:
      - Question, Reference, Bot Response
      - LLM Scores (Semantic, BLEU, ROUGE, F1, Exact Match, Confidence, Hallucination Risk)
      - Composite Score
      - Model Used
   - Generates summary dashboard with pie and bar charts for aggregated results.

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
 `.env` file for HuggingFace token

---

## Running test
`./gradlew test `

---
## Test Data
-	Test cases are loaded from testData.json.
-	Each test case executes as a separate TestNG test via a data provider.

---
## Extent Spark Reports
-	HTML report: reports/semantic-report_<timestamp>.html
-	Screenshots captured only on failures in screenshots/.

---
## Keynotes
-	EmbeddingService uses ai.djl.huggingface:tokenizers
-	GovGPTSemanticTest runs all JSON test cases dynamically.
-	Screenshots and logs are captured only on failures.
-	Framework is designed for scalability and reusability.

---