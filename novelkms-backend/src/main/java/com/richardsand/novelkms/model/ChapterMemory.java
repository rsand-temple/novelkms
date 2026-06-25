package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-chapter "memory document": a standardized summary of one chapter,
 * generated explicitly by the author (AI-filled from the memory template, then
 * optionally hand-edited). There is at most one current memory document per
 * chapter ({@code chapter_memory.chapter_id} is unique); regenerating overwrites
 * it and refreshes {@code generatedAt}.
 *
 * <p>Memory documents are continuity context, not review artifacts: when a
 * chapter review runs, the memory documents of all preceding chapters (in linear
 * book order) are concatenated and supplied to the model as a "story so far"
 * block. {@code generatedAt} is the basis for staleness reporting — a document
 * older than its chapter's latest scene edit is stale content; a document older
 * than a later chapter's is out of sequence (see {@code ChapterMemoryStatus}).
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

    @JsonProperty
    private Instant generatedAt;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
