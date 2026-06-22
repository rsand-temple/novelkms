package com.richardsand.novelkms.ai;

import java.util.List;

/**
 * Provider-agnostic input for a chapter review. Assembled by
 * {@code AiReviewService} from the manuscript; consumed by an {@link AiProvider}.
 *
 * @param apiKey         decrypted provider API key (never logged or persisted)
 * @param model          model identifier, e.g. {@code "gpt-5.4"}
 * @param chapterLabel   display label, e.g. {@code "Chapter 3"} or the title
 * @param chapterSubtitle optional subtitle (may be null/blank)
 * @param chapterText    plain-text chapter body (HTML stripped, scenes joined)
 * @param categories     review categories to consider
 */
public record ChapterReviewRequest(
        String apiKey,
        String model,
        String chapterLabel,
        String chapterSubtitle,
        String chapterText,
        List<String> categories) {
}
