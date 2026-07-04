package com.richardsand.novelkms.model.chapter;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-chapter, per-provider "memory document": a standardized summary of one
 * chapter, generated explicitly by the author (AI-filled from the memory
 * template, then optionally hand-edited). There is at most one current memory
 * document per (chapter, provider) — {@code chapter_memory} is unique on
 * {@code (chapter_id, provider)} (V36) — so each configured provider keeps its
 * own document and regenerating with a given provider overwrites that provider's
 * document and refreshes {@code generatedAt}.
 *
 * <p>Memory documents are continuity context, not review artifacts: when a
 * chapter review runs, the memory documents of all preceding chapters (in linear
 * book order) are concatenated and supplied to the model as a "story so far"
 * block. For each preceding chapter the review prefers the generating provider's
 * own document, falling back to that chapter's most-recently-updated document of
 * any provider. {@code generatedAt} is the basis for staleness reporting — a
 * document older than its chapter's latest scene edit is stale content; a
 * document older than a later chapter's is out of sequence (see
 * {@code ChapterMemoryStatus}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterMemory {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID chapterId;

    /**
     * The AI provider this document belongs to (e.g. {@code OPENAI},
     * {@code ANTHROPIC}, {@code GEMINI}). A chapter has at most one memory
     * document per provider.
     */
    @JsonProperty
    private String provider;

    @JsonProperty
    private String content;

    /** {@code AI} (provider-generated) or {@code EDITED} (author hand-edited). */
    @JsonProperty
    private String source;

    /** Memory-generation prompt version, e.g. {@code memory-v1}; null for a purely hand-authored edit. */
    @JsonProperty
    private String promptVersion;

    /** Model that generated the document; null for a hand-authored edit. */
    @JsonProperty
    private String model;

    /**
     * One-time author guidance supplied when this document was last generated
     * (e.g. "treat the letter as forged"), or null. Not part of the
     * memory-template override cascade — just an addendum sent with that one
     * generation call, kept here as provenance and as the pre-fill source for
     * the next regeneration's guidance field.
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
