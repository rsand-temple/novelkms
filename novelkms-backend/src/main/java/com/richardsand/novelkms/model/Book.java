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

    // ── Page layout ───────────────────────────────────────────────────────────

    /** Whether page layout (size, margins) is applied during preview and export. */
    @JsonProperty
    private boolean pageLayoutEnabled;

    /** Named preset: LETTER, A4, TRADE_PB, MASS_MARKET, HARDBACK, CUSTOM. */
    @JsonProperty
    private String pageSizePreset;

    /** Page width in inches — only meaningful when pageSizePreset = CUSTOM. */
    @JsonProperty
    private Double pageWidthIn;

    /** Page height in inches — only meaningful when pageSizePreset = CUSTOM. */
    @JsonProperty
    private Double pageHeightIn;

    @JsonProperty
    private Double pageMarginTopIn;

    @JsonProperty
    private Double pageMarginBottomIn;

    /** Inner (binding-side) margin in inches. */
    @JsonProperty
    private Double pageMarginInnerIn;

    @JsonProperty
    private Double pageMarginOuterIn;

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
