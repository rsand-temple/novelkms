package com.richardsand.novelkms.ai;

/**
 * Provider-agnostic input for generating one chapter's editorial. Assembled by
 * {@code AiReviewService} from the chapter's prose plus the same context inputs a
 * chapter review uses; consumed by {@link AiProvider#generateEditorial}.
 *
 * <p>Distinct from a review: there are no recommendations and no JSON contract —
 * the provider returns a short free-text editorial reading (tone, genre drift,
 * character arcs, storyline evolution), not discrete findings, and deliberately
 * does not surface line-level spelling/grammar. Distinct from a memory document
 * and a summary in that its output is never consumed by any other AI function.
 *
 * @param apiKey           decrypted provider API key (never logged or persisted)
 * @param model            model identifier, e.g. {@code "gpt-5.4"}
 * @param chapterLabel     display label for the chapter, e.g. {@code "Chapter 7"} or a title
 * @param subtitle         optional chapter subtitle (null/blank when none)
 * @param chapterText      plain-text body of the chapter (HTML stripped, scenes joined)
 * @param priorContext     optional "story so far" continuity context: the memory
 *                         documents of the preceding chapters, concatenated in
 *                         book order. Null/blank for the first chapter or when no
 *                         preceding memory documents exist. Presented as context
 *                         only — not material to be reviewed.
 * @param referenceContext optional reference material the manuscript must respect
 *                         (pinned Codex canon/voice entries). Null when nothing is
 *                         pinned or the author opted out of pinned context.
 * @param userGuidance     optional one-time author note for this generation only,
 *                         appended to the user message as a clearly-fenced
 *                         addendum. Null/blank when not supplied.
 */
public record EditorialRequest(
        String apiKey,
        String model,
        String chapterLabel,
        String subtitle,
        String chapterText,
        String priorContext,
        String referenceContext,
        String userGuidance) {
}
