package utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AxeAccessibility {

    private static final Logger log = LogManager.getLogger(AxeAccessibility.class);
    private static final String AXE_RESOURCE = "axe.min.js";

    /**
     * Analyze the page for accessibility violations by injecting axe.min.js.
     *
     * @param driver Selenium WebDriver
     * @return List of violations as JsonObjects
     */
    public static List<JsonObject> analyzePage(WebDriver driver) {
        List<JsonObject> violationsList = new ArrayList<>();

        try {
            // ✅ Load axe.min.js from classpath (src/test/resources)
            InputStream is = AxeAccessibility.class.getClassLoader().getResourceAsStream(AXE_RESOURCE);
            if (is == null) {
                throw new IllegalStateException("axe.min.js not found on classpath under test/resources!");
            }
            String axeScript = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Inject axe.min.js into the page
            js.executeScript(axeScript);

            // Run axe asynchronously
            Object rawResult = js.executeAsyncScript(
                    "var callback = arguments[arguments.length - 1];" +
                            "axe.run().then(function(results) { callback(JSON.stringify(results)); });"
            );

            if (rawResult == null) {
                log.warn("Axe JS returned null result. Skipping accessibility analysis.");
                return violationsList;
            }

            String resultJson = rawResult.toString();
            JsonObject resultObj = JsonParser.parseString(resultJson).getAsJsonObject();
            JsonArray violations = resultObj.getAsJsonArray("violations");

            for (int i = 0; i < violations.size(); i++) {
                violationsList.add(violations.get(i).getAsJsonObject());
            }

            log.info("Accessibility violations found: {}", violationsList.size());

        } catch (Exception e) {
            log.error("Error during Axe accessibility analysis", e);
        }

        return violationsList;
    }
}