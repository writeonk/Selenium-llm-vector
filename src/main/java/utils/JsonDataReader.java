package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonDataReader {

    // Read from file path
    public static List<TestCase> read(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("JSON file not found: " + filePath);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Read as generic List<Object> to handle nested arrays
            List<Object> rawList = mapper.readValue(file, new TypeReference<List<Object>>() {
            });
            return flattenTestCases(rawList, mapper);

        } catch (Exception e) {
            throw new RuntimeException("Failed to read test data: " + filePath, e);
        }
    }

    // Read from InputStream (classpath)
    public static List<TestCase> read(InputStream inputStream) {
        try {
            if (inputStream == null) {
                throw new RuntimeException("InputStream is null. JSON not found in resources.");
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Read as generic List<Object> to handle nested arrays
            List<Object> rawList = mapper.readValue(inputStream, new TypeReference<List<Object>>() {
            });
            return flattenTestCases(rawList, mapper);

        } catch (Exception e) {
            throw new RuntimeException("Failed to read test data from input stream", e);
        }
    }

    // Helper method to flatten nested arrays into a single List<TestCase>
    private static List<TestCase> flattenTestCases(List<Object> rawList, ObjectMapper mapper) {
        List<TestCase> testCases = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map) {
                // single TestCase object
                testCases.add(mapper.convertValue(item, TestCase.class));
            } else if (item instanceof List) {
                // nested array of TestCase objects
                for (Object nestedItem : (List<?>) item) {
                    testCases.add(mapper.convertValue(nestedItem, TestCase.class));
                }
            }
        }
        return testCases;
    }
}