package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class JsonDataReader {

    // Existing method for file path
    public static List<TestCase> read(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("JSON file not found: " + filePath);
            }

            return mapper.readValue(file, new TypeReference<List<TestCase>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read test data: " + filePath, e);
        }
    }

    // NEW: Read from InputStream (classpath)
    public static List<TestCase> read(InputStream inputStream) {
        try {
            if (inputStream == null) {
                throw new RuntimeException("InputStream is null. JSON not found in resources.");
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(inputStream, new TypeReference<List<TestCase>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read test data from input stream", e);
        }
    }
}