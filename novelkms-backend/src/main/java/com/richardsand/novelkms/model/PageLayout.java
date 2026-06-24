package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One {@code page_layout} row at some scope, carrying the resolved or stored
 * page-layout values. Page layout affects export/preview only — never the live
 * editor.
 *
 * <p>{@code scope} is {@code SYSTEM | PROJECT | BOOK}. {@code projectId} is set
 * for PROJECT rows and {@code bookId} for BOOK rows; both are null for SYSTEM.
 * Resolution for a book is {@code BOOK -> PROJECT -> SYSTEM}; inspect
 * {@code scope} on a resolve response to know whether the book/project is
 * overriding or inheriting (drives the dialog's per-tab override toggle).
 *
 * <p>The value getters intentionally keep the {@code page*} names the former
 * {@code book} columns used, so the export path and the frontend payload are
 * unchanged by the move out of the book entity.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageLayout {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String scope;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private boolean pageLayoutEnabled;

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

    @JsonProperty
    private Double pageMarginInnerIn;

    @JsonProperty
    private Double pageMarginOuterIn;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
