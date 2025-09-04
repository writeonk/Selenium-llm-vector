package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

public class JsonDataReader {
    public static List<TestCase> read(String resourcePath) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, new TypeReference<List<TestCase>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read test data: " + resourcePath, e);
        }
    }
}