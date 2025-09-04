package common;

import com.aventstack.extentreports.*;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.JsonFormatter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.safari.SafariDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;

public class TestBase {

    public static Properties properties;
    public static WebDriver driver;

    protected static final Logger log = LogManager.getLogger(TestBase.class);

    public static ExtentReports extent;
    public static ExtentTest test;

    private static final String dt = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
    public static final String REPORT_PATH = "reports/Qa_GovBot_Analytics_Report" + dt + ".html";
    private static final String JSON_ARCHIVE = "target/json/jsonArchive.json";

    @BeforeSuite(alwaysRun = true)
    public void setUpSuite() throws IOException {
        loadProperties();
        initExtentReport();
        launchBrowser();
    }

    @BeforeMethod(alwaysRun = true)
    public void startTest(Method method) {
        if (extent != null) {
            test = extent.createTest(method.getName());
            log.info("===== STARTING TEST: {} =====", method.getName());
        }
    }

    @AfterMethod(alwaysRun = true)
    public void captureResult(ITestResult result) {
        if (test == null) return;

        try {
            String testName = result.getName();
            if (result.getStatus() == ITestResult.FAILURE) {
                String screenshotPath = captureScreenshot(driver, testName);
                log.error("Test FAILED: {} | Reason: {}", testName, result.getThrowable().getMessage());
                test.fail("Test case FAILED: " + testName,
                        MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());

            } else if (result.getStatus() == ITestResult.SKIP) {
                log.warn("Test SKIPPED: {}", testName);
                test.skip("Test case SKIPPED: " + testName);

            } else if (result.getStatus() == ITestResult.SUCCESS) {
                log.info("Test PASSED: {}", testName);
                test.pass("Test case PASSED: " + testName);
            }
        } catch (Exception e) {
            log.error("Error while logging test result", e);
        }
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        if (driver != null) {
            driver.quit();
            log.info("Browser closed successfully.");
        }
        if (extent != null) {
            extent.flush();
            log.info("ExtentReports flushed. Report available at: {}", REPORT_PATH);
        }
    }

    private void loadProperties() throws IOException {
        try (FileReader reader = new FileReader("src/test/resources/config.properties")) {
            properties = new Properties();
            properties.load(reader);
            log.info("Config properties loaded successfully.");
        } catch (FileNotFoundException ex) {
            log.error("Config file not found!", ex);
        }
    }

    private void initExtentReport() {
        ExtentSparkReporter spark = new ExtentSparkReporter(REPORT_PATH);
        JsonFormatter json = new JsonFormatter(JSON_ARCHIVE);

        extent = new ExtentReports();
        extent.attachReporter(spark, json);

        extent.setSystemInfo("OS", System.getProperty("os.name"));
        if (properties != null) {
            extent.setSystemInfo("Browser", properties.getProperty("BrowserName", "chrome"));
            extent.setSystemInfo("Environment", properties.getProperty("Environment", "QA"));
        }

        spark.config().setDocumentTitle("Chatbot Automation Report");
        spark.config().setReportName("Semantic + UI Validation Suite");
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setTimelineEnabled(true);
        spark.config().setOfflineMode(true);

        log.info("ExtentReports initialized: {}", REPORT_PATH);
    }

    private void launchBrowser() {
        if (properties == null) {
            log.error("Properties not loaded. Cannot launch browser.");
            return;
        }

        String browser = properties.getProperty("BrowserName", "chrome");
        switch (browser.toLowerCase()) {
            case "chrome":
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver();
                log.info("Launched Chrome browser.");
                break;

            case "firefox":
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions options = new FirefoxOptions();
                options.setLogLevel(FirefoxDriverLogLevel.TRACE);
                driver = new FirefoxDriver(options);
                log.info("Launched Firefox browser.");
                break;

            case "safari":
                driver = new SafariDriver();
                log.info("Launched Safari browser.");
                break;

            default:
                log.error("Unsupported browser: {}", browser);
        }

        if (driver != null) {
            driver.manage().window().maximize();
            driver.manage().deleteAllCookies();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    public String captureScreenshot(WebDriver driver, String screenshotName) {
        String path = System.getProperty("user.dir") + "/screenshots/" + screenshotName + ".png";
        try {
            if (driver != null) {
                File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.createDirectories(Paths.get(System.getProperty("user.dir") + "/screenshots/"));
                Files.copy(screenshot.toPath(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
                log.info("Screenshot captured: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to capture screenshot: {}", screenshotName, e);
        }
        return path;
    }

    public void openURL(String url) {
        if (driver != null) {
            log.info("Navigating to URL: {}", url);
            driver.get(url);
        } else {
            log.error("Driver is null. Cannot open URL: {}", url);
        }
    }
}