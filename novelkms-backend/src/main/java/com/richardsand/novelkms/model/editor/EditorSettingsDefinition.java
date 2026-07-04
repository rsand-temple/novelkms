package com.richardsand.novelkms.model.editor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The "document settings" bundle that drives the manuscript editor surface:
 * base font, line height, paragraph indents/spacing, and scene-break rendering.
 *
 * <p>Serialized to/from the {@code editor_settings.definition} JSON column and
 * exposed directly in the API. The field names match the keys the frontend
 * editor already consumes (see {@code useProjectSettings} / the editor CSS
 * variables), so no translation layer is required.
 *
 * <p>Values are CSS-ready strings (e.g. {@code "1.5em"}, {@code "1.9"},
 * {@code "0"}). {@code sceneBreakStyle} is one of {@code "* * *" | "#" | "rule"}.
 *
 * <p>{@code @Data} provides getters + setters so Jackson can deserialize incoming
 * PUT bodies; unknown fields are ignored for forward-compatibility (a newer
 * client can send extra keys against an older server without error).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EditorSettingsDefinition {
    private String fontFamily;
    private String fontSize;
    private String lineHeight;
    private String firstLineIndent;
    private String spacingAfter;
    private String sceneBreakStyle;
    private String sceneBreakSpacingAbove;
    private String sceneBreakSpacingBelow;
}
