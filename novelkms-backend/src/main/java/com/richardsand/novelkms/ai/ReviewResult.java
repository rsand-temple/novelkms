package com.richardsand.novelkms.ai;

import java.util.List;

/**
 * Provider-agnostic result of a chapter review. {@code rawJson} is the raw
 * provider response text, persisted for audit. {@code promptVersion} identifies
 * the prompt template used so historical reviews remain interpretable as prompts
 * evolve.
 */
public record ReviewResult(
        List<Recommendation> recommendations,
        String rawJson,
        String promptVersion) {

    /**
     * One atomic editorial recommendation as returned by the model, before it is
     * assigned a sequence number and persisted.
     */
    public record Recommendation(
            String category,
            String severity,
            String location,
            String recommendation,
            String codexCategory,
            String codexTitle,
            String anchorText) {
    }
}
