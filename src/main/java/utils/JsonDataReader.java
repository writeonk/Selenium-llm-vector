package utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

public class JsonDataReader {

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
}