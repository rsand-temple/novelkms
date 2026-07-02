package com.richardsand.novelkms.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One structured field in a codex category's {@link CodexSchema}. Field
 * definitions are data (stored as JSON on {@code codex_category.schema}), not
 * code, so a category's shape can change without a migration and a future UI
 * can let authors define their own categories and fields.
 *
 * <p>{@code key} is the stable identifier used as the property name inside a
 * codex entry's {@code scene.structured_data} JSON object. {@code type} is one
 * of {@code SHORT_TEXT}, {@code LONG_TEXT}, or {@code SELECT}; {@code options}
 * supplies the choices for a {@code SELECT}. {@code help} is optional guidance
 * shown under the field to prompt the author. {@code feedsAi} decides whether
 * the field's value is serialized into an AI review's reference-context block —
 * private fields (e.g. author notes) stay out of the prompt.
 *
 * <p>There is deliberately no {@code required} concept: no codex field is ever
 * mandatory.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexField {

    @JsonProperty
    private String key;

    @JsonProperty
    private String label;

    /** SHORT_TEXT | LONG_TEXT | SELECT. */
    @JsonProperty
    private String type;

    /** Choices for a SELECT field; null/empty for text fields. */
    @JsonProperty
    private List<String> options;

    /** Optional author-facing guidance shown beneath the field. */
    @JsonProperty
    private String help;

    /** When true, this field's value is included in AI review reference context. */
    @JsonProperty("feedsAi")
    private boolean feedsAi;
}
