package utils;

import java.util.*;

public class NLPUtils {

    public static double computeBLEU(String reference, String candidate) {
        // Very simple unigram BLEU approximation
        String[] refTokens = reference.split("\\s+");
        String[] candTokens = candidate.split("\\s+");
        Set<String> refSet = new HashSet<>(Arrays.asList(refTokens));
        int match = 0;
        for (String w : candTokens) if (refSet.contains(w)) match++;
        return candTokens.length == 0 ? 0.0 : (double) match / candTokens.length;
    }

    public static double computeROUGE(String reference, String candidate) {
        // Simple recall-oriented overlap
        String[] refTokens = reference.split("\\s+");
        String[] candTokens = candidate.split("\\s+");
        Set<String> refSet = new HashSet<>(Arrays.asList(refTokens));
        int overlap = 0;
        for (String w : candTokens) if (refSet.contains(w)) overlap++;
        return refTokens.length == 0 ? 0.0 : (double) overlap / refTokens.length;
    }
}