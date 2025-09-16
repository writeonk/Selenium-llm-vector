package utils;

import java.util.HashSet;
import java.util.Set;

public class MultiTurnService {

    // Check if two answers are consistent (dummy implementation)
    public boolean isConsistent(String previousAnswer, String currentAnswer) {
        if (previousAnswer == null || currentAnswer == null) return true;
        // For demo: consider consistent if overlap in words > 50%
        Set<String> prevWords = new HashSet<>(Set.of(previousAnswer.toLowerCase().split("\\s+")));
        Set<String> currWords = new HashSet<>(Set.of(currentAnswer.toLowerCase().split("\\s+")));
        prevWords.retainAll(currWords);
        return prevWords.size() >= Math.min(currWords.size(), 3); // simple heuristic
    }

    // Count repeated tokens in the text
    public int countRepeatedTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] tokens = text.split("\\s+");
        Set<String> unique = new HashSet<>();
        int repeats = 0;
        for (String t : tokens) {
            if (!unique.add(t.toLowerCase())) repeats++;
        }
        return repeats;
    }

    // Compute type-token ratio (unique tokens / total tokens)
    public double typeTokenRatio(String text) {
        if (text == null || text.isEmpty()) return 1.0;
        String[] tokens = text.split("\\s+");
        Set<String> unique = new HashSet<>();
        for (String t : tokens) unique.add(t.toLowerCase());
        return ((double) unique.size()) / tokens.length;
    }
}