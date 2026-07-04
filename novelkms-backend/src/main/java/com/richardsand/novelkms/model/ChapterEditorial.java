package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-chapter, per-provider "editorial": a short editorial reading of one
 * chapter — the AI's overall take on tone, genre drift, character arcs, and
 * storyline evolution. It is generated explicitly by the author (AI-filled from
 * the chapter prose, then optionally hand-edited) and is deliberately brief
 * (about half a page or less). There is at most one current editorial per
 * (chapter, provider) — {@code chapter_editorial} is unique on
 * {@code (chapter_id, provider)} (V36) — so each configured provider keeps its
 * own editorial and regenerating with a given provider overwrites that
 * provider's editorial and refreshes {@code generatedAt}.
 *
 * <p>Editorials are their own artifact family, entirely separate from memory
 * documents (V24) and summaries (V25). The defining difference from a memory
 * document is that an editorial is <em>never consumed by any other AI
 * function</em> — it exists purely for the author's edification. It is also
 * distinct from an {@code ai_review}: an editorial gives an impressionistic
 * overall reading rather than discrete, triageable line-level findings, and it
 * deliberately does not flag spelling or grammar unless egregious.
 *
 * <p>When generated, an editorial draws on the same context inputs a chapter
 * review does — the preceding chapters' memory documents ("story so far"),
 * resolved with the same generating-provider preference — and any pinned Codex
 * reference entries. Nothing downstream ever reads it back.
 *
 * <p>{@code content} is authored TipTap HTML (edited in EditorPanel through the
 * chapter's Editorial nav leaf, the same as memory/summary).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterEditorial {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID chapterId;

    /**
     * The AI provider this editorial belongs to (e.g. {@code OPENAI},
     * {@code ANTHROPIC}, {@code GEMINI}). A chapter has at most one editorial
     * per provider.
     */
    @JsonProperty
    private String provider;

    @JsonProperty
    private String content;

    /** {@code AI} (provider-generated) or {@code EDITED} (author hand-edited). */
    @JsonProperty
    private String source;

    /** Editorial-generation prompt version, e.g. {@code chapter-editorial-v1}; null for a purely hand-authored edit. */
    @JsonProperty
    private String promptVersion;

    /** Model that generated the editorial; null for a hand-authored edit. */
    @JsonProperty
    private String model;

    /**
     * One-time author guidance supplied when this editorial was last generated,
     * or null. Just an addendum sent with that one generation call, kept here as
     * provenance and as the pre-fill source for the next regeneration's guidance
     * field. Not part of any persistent override cascade.
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
