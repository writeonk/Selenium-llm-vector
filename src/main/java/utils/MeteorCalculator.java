package utils;

import java.util.*;

/**
 * Stub for METEOR metric.
 * Currently uses simple token overlap.
 * Can be extended to call HuggingFace METEOR via DJL or Python bridge.
 */
public class MeteorCalculator {

    public double computeScore(String reference, String hypothesis) {
        if (reference == null || hypothesis == null || reference.isEmpty()) return 0.0;

        String[] refTokens = reference.toLowerCase().split("\\s+");
        String[] hypTokens = hypothesis.toLowerCase().split("\\s+");

        Set<String> refSet = new HashSet<>(Arrays.asList(refTokens));
        Set<String> hypSet = new HashSet<>(Arrays.asList(hypTokens));

        int overlap = 0;
        for (String token : refSet) {
            if (hypSet.contains(token)) overlap++;
        }

        // Placeholder: overlap ratio
        // TODO: Replace with real METEOR scoring via HuggingFace
        return (double) overlap / Math.max(refSet.size(), 1);
    }
}