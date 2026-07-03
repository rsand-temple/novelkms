package com.richardsand.novelkms.ai;

/**
 * Provider-agnostic input for generating a whole-book summary. Assembled by
 * {@code AiReviewService} by concatenating the book's chapter summaries in linear
 * book order; consumed by {@link AiProvider#generateBookSummary}.
 *
 * <p>The model is given only the chapter summaries, never the manuscript prose —
 * a full-length book is too large to summarize reliably in one pass, so the
 * already-distilled chapter summaries are the sole input. The provider returns a
 * cohesive synopsis of at most {@code maxWords} words verbatim.
 *
 * @param apiKey           decrypted provider API key (never logged or persisted)
 * @param model            model identifier, e.g. {@code "gpt-5.4"}
 * @param bookTitle        display title of the book (may be blank)
 * @param chapterSummaries the chapter summaries concatenated in book order, each
 *                         under a {@code Chapter N: Title} heading; never blank
 * @param maxWords         hard upper bound on the synopsis length, e.g. 1000.
 *                         Passed to the system prompt only when the built-in
 *                         constant is the active template (it contains a
 *                         {@code %1$d} format token); user-provided templates
 *                         state their own word limit in plain text.
 * @param userGuidance     optional one-time author note for this generation only,
 *                         appended to the user message as a clearly-fenced
 *                         addendum. Null/blank when not supplied.
 * @param systemPrompt     the resolved AI system prompt for this generation, from
 *                         {@code AiPromptTemplateDao} (book → project → user →
 *                         system default). When non-blank the provider uses it
 *                         verbatim; {@code maxWords} is then advisory only
 *                         (the prompt itself states the limit).
 */
public record BookSummaryRequest(
        String apiKey,
        String model,
        String bookTitle,
        String chapterSummaries,
        int    maxWords,
        String userGuidance,
        String systemPrompt) {
}
