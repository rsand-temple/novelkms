package com.richardsand.novelkms.model.book;

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
public class Book {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private String title;

    @JsonProperty
    private String subtitle;

    @JsonProperty
    private String shortTitle;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String notes;

    // ── Cover image ───────────────────────────────────────────────────────────

    /**
     * True when a cover image has been uploaded for this book.
     * The image bytes themselves are never serialized here — they are served
     * via GET /api/books/{id}/cover-image.  This flag lets the frontend know
     * whether to render a thumbnail or a placeholder without fetching the blob.
     */
    @JsonProperty
    private boolean hasCoverImage;

    // ── Import provenance ─────────────────────────────────────────────────────

    /**
     * Original filename of the file this book was imported from
     * (e.g. "TheAloneMan.docx"). Null for books created from scratch.
     */
    @JsonProperty
    private String importedFrom;

    /**
     * UTC timestamp of the import run. Null for books created from scratch.
     * Read-only from the UI; only set by the import pipeline.
     */
    @JsonProperty
    private Instant importedAt;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
