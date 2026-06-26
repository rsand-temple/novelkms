package com.richardsand.novelkms.ai;

/**
 * Provider-agnostic input for generating one chapter's memory document.
 * Assembled by {@code AiReviewService} from the chapter's prose and the resolved
 * memory template; consumed by an {@link AiProvider#generateMemory}.
 *
 * <p>Distinct from a review: there are no recommendations and no JSON contract.
 * The provider fills the {@code template} from the {@code chapterText} and
 * returns the resulting document text verbatim.
 *
 * @param apiKey       decrypted provider API key (never logged or persisted)
 * @param model        model identifier, e.g. {@code "gpt-5.4"}
 * @param chapterLabel display label for the chapter, e.g. {@code "Chapter 7"} or a title
 * @param chapterText  plain-text body of the chapter (HTML stripped, scenes joined)
 * @param template     the resolved memory-document template to fill in
 *                     (book -&gt; project -&gt; user -&gt; system); never null/blank
 * @param userGuidance optional one-time author note for this generation only,
 *                     appended to the user message as a clearly-fenced
 *                     addendum. Null/blank when not supplied.
 */
public record MemoryRequest(
        String apiKey,
        String model,
        String chapterLabel,
        String chapterText,
        String template,
        String userGuidance) {
}
