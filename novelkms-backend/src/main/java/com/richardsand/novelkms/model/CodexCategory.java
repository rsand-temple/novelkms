package com.richardsand.novelkms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An entry in the master list of codex categories. Drives the category
 * dropdowns in the UI and, for is_default rows, the chapters seeded into a new
 * codex. Adjustable in the future (it is a data table, not an enum).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexCategory {

    @JsonProperty
    private String categoryKey;

    @JsonProperty
    private String label;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String icon;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
