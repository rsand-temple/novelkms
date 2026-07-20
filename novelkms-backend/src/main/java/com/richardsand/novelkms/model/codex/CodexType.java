package com.richardsand.novelkms.model.codex;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The per-instance read model for a Codex Type. A "Type" is stored as a codex
 * category chapter row (a {@code chapter} with {@code codex_id} set and
 * {@code book_id} NULL); this DTO projects that row together with its own
 * normalized field set from {@code codex_type_field}.
 *
 * <p>Introduced by the Extensible Codex feature (phase E2). Until now a
 * category's field schema was <em>system-global by key</em> — every project's
 * CHARACTER type borrowed one shared {@link CodexSchema} off
 * {@code codex_category.field_schema}. From here on each Type instance owns its
 * fields, so authors can rename / reorder / remove them per project without
 * touching any other project. This read model is what the entry form and AI
 * fill resolve against once E3 cuts the live read path over to it.
 *
 * <ul>
 *   <li>{@code id} — the Type's chapter-row id (the same id used to fetch it and
 *       to create entry scenes beneath it).</li>
 *   <li>{@code name} — the author-facing Type name ({@code chapter.title}).</li>
 *   <li>{@code description} — optional per-Type description
 *       ({@code chapter.codex_type_description}); null until the type editor (E4)
 *       writes it.</li>
 *   <li>{@code systemKey} — the seeded category key ({@code chapter.codex_category},
 *       e.g. CHARACTER, VOICE) used only for AI-promotion mapping; <b>null for
 *       author-created types</b>. Renaming a Type changes {@code name}, never
 *       this key.</li>
 *   <li>{@code fields} — the Type's <em>active</em> fields in display order;
 *       soft-removed fields are excluded. Reuses {@link CodexField} verbatim, so
 *       the form is schema-source-agnostic.</li>
 * </ul>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexType {

    /** The Type's chapter-row id. */
    @JsonProperty
    private UUID id;

    /** Author-facing Type name (chapter.title). */
    @JsonProperty
    private String name;

    /** Optional per-Type description; null until the type editor writes it. */
    @JsonProperty
    private String description;

    /**
     * Seeded category key for AI-promotion mapping (chapter.codex_category);
     * null for author-created types. Immutable across renames.
     */
    @JsonProperty
    private String systemKey;

    /** Active fields in display order (soft-removed fields excluded). */
    @JsonProperty
    private List<CodexField> fields;
}
