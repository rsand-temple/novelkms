package com.richardsand.novelkms.ai;

import java.util.List;

/**
 * Provider-agnostic input for a review. A chapter review and a scene review are
 * the same kind of request; they differ only in scope, carried by
 * {@code scopeWord}. Assembled by {@code AiReviewService} from the manuscript;
 * consumed by an {@link AiProvider}.
 *
 * <p>Replaces the former {@code ChapterReviewRequest}.
 *
 * @param apiKey     decrypted provider API key (never logged or persisted)
 * @param model      model identifier, e.g. {@code "gpt-5.4"}
 * @param scopeWord  the prose unit word, lower-case: {@code "chapter"} or {@code "scene"}
 * @param unitLabel  display label, e.g. {@code "Chapter 3"} or a scene title
 * @param subtitle   optional subtitle (chapter subtitle; null/blank for scenes)
 * @param text       plain-text body (HTML stripped, scenes joined for a chapter)
 * @param categories review categories to consider
 */
public record ReviewRequest(
        String apiKey,
        String model,
        String scopeWord,
        String unitLabel,
        String subtitle,
        String text,
        List<String> categories) {
}
