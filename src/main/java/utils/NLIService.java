package utils;

/**
 * Stub for Natural Language Inference (NLI) / Contradiction detection.
 * Right now a simple heuristic.
 * Later: Replace with HuggingFace model (e.g. bart-large-mnli) via DJL.
 */
public class NLIService {

    public boolean isContradiction(String reference, String hypothesis) {
        if (reference == null || hypothesis == null) return false;

        // Placeholder: detect "not" as contradiction
        // TODO: Plug HuggingFace BART-MNLI with DJL here
        return (hypothesis.contains(" not ") && !reference.contains(" not "));
    }
}