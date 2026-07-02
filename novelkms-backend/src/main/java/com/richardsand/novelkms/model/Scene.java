package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scene {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID chapterId;

    @JsonProperty
    private String title;

    @JsonProperty
    private int displayOrder;

    /**
     * TipTap JSON document stored as a text string.
     * Null for a newly created scene with no content yet.
     */
    @JsonProperty
    private String content;

    /** Word count maintained by the application; updated on each save. */
    @JsonProperty
    private int wordCount;

    /**
     * Meaningful only for codex entries (scenes whose parent chapter is a codex
     * category). When true, the entry is shared with the AI as reference context
     * for chapter/scene reviews — see {@code AiReviewService.assembleReferenceContext}.
     * Defaults false; inert for manuscript scenes, the same way
     * {@code Chapter.resetsNumbering} is inert for codex chapters.
     */
    @JsonProperty
    private boolean aiContextPinned;

    /**
     * Meaningful only for codex entries. Holds a JSON object of structured field
     * values keyed by the field keys defined in the parent category's
     * {@link CodexCategory#getSchema()} (e.g. a CHARACTER entry's role, want,
     * arc). Null for manuscript scenes and for entries in unstructured
     * categories. The free {@link #content} body remains the long-form
     * description alongside these fields.
     */
    @JsonProperty
    private String structuredData;

    @JsonProperty
    private String notes;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
