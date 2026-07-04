package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-chapter, per-provider "chapter summary": a single human-readable
 * paragraph summarizing one chapter, generated explicitly by the author
 * (AI-filled from the chapter prose, then optionally hand-edited). There is at
 * most one current summary per (chapter, provider) — {@code chapter_summary} is
 * unique on {@code (chapter_id, provider)} (V36) — so each configured provider
 * keeps its own summary and regenerating with a given provider overwrites that
 * provider's summary and refreshes {@code generatedAt}.
 *
 * <p>Chapter summaries are an entirely separate artifact family from memory
 * documents (V24). Memory documents are structured continuity context fed into a
 * chapter review as "story so far"; chapter summaries are a clean readable
 * synopsis surfaced in the book's aggregated chapter-summary view and consumed
 * (in book order) as the sole input when generating the book summary, since a
 * full-length manuscript is too large to summarize reliably in one pass. When a
 * book summary is generated, each chapter contributes the generating provider's
 * own summary, falling back to that chapter's most-recently-updated summary of
 * any provider. Regenerating one never touches the other.
 *
 * <p>{@code generatedAt} is the basis for staleness reporting: a summary older
 * than its chapter's latest scene edit is stale content (see
 * {@code ChapterSummaryStatus}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterSummary {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID chapterId;

    /**
     * The AI provider this summary belongs to (e.g. {@code OPENAI},
     * {@code ANTHROPIC}, {@code GEMINI}). A chapter has at most one summary per
     * provider.
     */
    @JsonProperty
    private String provider;

    @JsonProperty
    private String content;

    /** {@code AI} (provider-generated) or {@code EDITED} (author hand-edited). */
    @JsonProperty
    private String source;

    /** Summary-generation prompt version, e.g. {@code chapter-summary-v1}; null for a purely hand-authored edit. */
    @JsonProperty
    private String promptVersion;

    /** Model that generated the summary; null for a hand-authored edit. */
    @JsonProperty
    private String model;

    /**
     * One-time author guidance supplied when this summary was last generated,
     * or null. Just an addendum sent with that one generation call, kept here
     * as provenance and as the pre-fill source for the next regeneration's
     * guidance field.
     */
    @JsonProperty
    private String userGuidance;

    @JsonProperty
    private Instant generatedAt;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
