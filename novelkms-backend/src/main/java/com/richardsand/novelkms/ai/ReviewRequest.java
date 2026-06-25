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
 * @param text             plain-text body (HTML stripped, scenes joined for a chapter)
 * @param categories       review categories to consider
 * @param formInstructions resolved editorial "form" block (persona/constraints);
 *                         the constant "functional" JSON contract is added by the
 *                         provider. Never null/blank — {@code AiReviewService}
 *                         resolves it (book -&gt; project -&gt; user -&gt; system)
 *                         before building the request.
 * @param priorContext    optional "story so far" continuity context: the memory
 *                         documents of the preceding chapters, concatenated in
 *                         book order. Null/blank for scene reviews, for the first
 *                         chapter, or when no preceding memory documents exist.
 *                         The provider presents it as context only — not material
 *                         to be reviewed.
 * @param referenceContext optional reference material the manuscript must respect
 *                         (e.g. pinned Codex canon/voice entries). Forward-prep:
 *                         currently always null; reserved for the Codex-context
 *                         increment. The provider emits the block only when present.
 */
public record ReviewRequest(
        String apiKey,
        String model,
        String scopeWord,
        String unitLabel,
        String subtitle,
        String text,
        List<String> categories,
        String formInstructions,
        String priorContext,
        String referenceContext) {
}
