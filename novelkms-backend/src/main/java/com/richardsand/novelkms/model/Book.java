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

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
    
    @JsonProperty
    private boolean pageLayoutEnabled;

    @JsonProperty
    private String pageSizePreset;     // "LETTER" | "A4" | "TRADE_PAPERBACK" |
                                       // "MASS_MARKET" | "HARDBACK" | "CUSTOM"

    @JsonProperty
    private Double pageWidthIn;        // null for all presets except CUSTOM

    @JsonProperty
    private Double pageHeightIn;       // null for all presets except CUSTOM

    @JsonProperty
    private double pageMarginTopIn;

    @JsonProperty
    private double pageMarginBottomIn;

    @JsonProperty
    private double pageMarginInnerIn;  // spine side

    @JsonProperty
    private double pageMarginOuterIn;  // edge side
}
