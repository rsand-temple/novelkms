package com.richardsand.novelkms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The formatting bundle a style carries. Serialized to/from the
 * {@code style.definition} JSON column and exposed directly in the API.
 *
 * Values are CSS-ready strings (e.g. "1.5rem", "1.5em", "0") matching what the
 * editor's StyledParagraph already consumes. {@code bold}/{@code italic} are
 * applied by the frontend as font-weight / font-style on the paragraph base.
 *
 * {@code @Data} provides getters + setters so Jackson can deserialize incoming
 * PUT bodies; unknown fields are ignored for forward-compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StyleDefinition {
    private String  fontFamily;
    private String  fontSize;
    private boolean bold;
    private boolean italic;
    private String  firstLineIndent;
    private String  textIndent;
    private String  spacingBefore;
    private String  spacingAfter;
}
