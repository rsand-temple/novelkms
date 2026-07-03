package com.richardsand.novelkms.ai;

/**
 * Provider-agnostic input for generating one chapter's summary. Assembled by
 * {@code AiReviewService} from the chapter's prose; consumed by
 * {@link AiProvider#generateChapterSummary}.
 *
 * <p>Distinct from a review and from a memory document: there are no
 * recommendations, no JSON contract, and no structural template. The provider
 * reads the chapter text and returns a single readable summary paragraph verbatim.
 *
 * @param apiKey       decrypted provider API key (never logged or persisted)
 * @param model        model identifier, e.g. {@code "gpt-5.4"}
 * @param chapterLabel display label for the chapter, e.g. {@code "Chapter 7"} or a title
 * @param chapterText  plain-text body of the chapter (HTML stripped, scenes joined)
 * @param userGuidance optional one-time author note for this generation only,
 *                     appended to the user message as a clearly-fenced
 *                     addendum. Null/blank when not supplied.
 * @param systemPrompt the resolved AI system prompt for this generation, from
 *                     {@code AiPromptTemplateDao} (book → project → user → system
 *                     default). When non-blank the provider uses it verbatim
 *                     instead of its built-in constant.
 */
public record SummaryRequest(
        String apiKey,
        String model,
        String chapterLabel,
        String chapterText,
        String userGuidance,
        String systemPrompt) {
}
