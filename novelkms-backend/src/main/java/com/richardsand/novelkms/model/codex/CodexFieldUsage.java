package com.richardsand.novelkms.model.codex;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The type-editor read model for one field of a Codex Type, carrying the two
 * pieces of state the editor needs beyond a plain {@link CodexField}: whether
 * the field is soft-removed and how many entries currently hold a value for it.
 *
 * <p>Introduced by the Extensible Codex feature (phase E6, non-destructive field
 * soft-remove / restore). It deliberately mirrors {@link CodexField}'s attribute
 * shape rather than wrapping it, so the client reads one flat object; the extra
 * {@code removed} and {@code entryCount} fields stay out of {@link CodexField}
 * and {@link CodexType} so the entry form and AI reference contracts keep seeing
 * only active fields with no editor-only noise.
 *
 * <p>This model backs {@code GET /codex/types/{typeId}/fields/usage}, which
 * returns <em>all</em> of a Type's fields (active and removed) in display order.
 * The editor uses it for two things at once: the "Removed fields" area (the
 * subset where {@code removed} is true, each shown with its {@code entryCount}
 * and a Restore action) and the pre-removal warning for an active field (its
 * {@code entryCount} decides whether removing it would hide existing data).
 *
 * <ul>
 *   <li>{@code key} — the immutable {@code field_key}; the property name inside
 *       an entry's {@code scene.structured_data}.</li>
 *   <li>{@code label} / {@code type} / {@code options} / {@code help} /
 *       {@code feedsAi} — mirror {@link CodexField} verbatim.</li>
 *   <li>{@code removed} — true when the field is soft-removed
 *       ({@code codex_type_field.deleted_at} is set); it no longer appears on the
 *       entry form but its stored values are preserved.</li>
 *   <li>{@code entryCount} — how many of the Type's entries have a non-blank
 *       value for this field key right now. Computed in Java over the entries'
 *       {@code structured_data} (portable across H2 and PostgreSQL), never from a
 *       dialect-specific JSON operator.</li>
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodexFieldUsage {

    /** Immutable field key. */
    @JsonProperty
    private String key;

    /** User-facing field label. */
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

    /** True when the field is soft-removed (hidden from the form, values kept). */
    @JsonProperty
    private boolean removed;

    /** Number of entries with a non-blank value for this field key. */
    @JsonProperty
    private int entryCount;
}
