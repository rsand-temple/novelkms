package com.richardsand.novelkms.model.codex;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The structured-field schema for a codex category. Stored as JSON on
 * {@code codex_category.schema} and returned to the frontend on the categories
 * lookup so the codex-entry editor can render a schema-driven form above the
 * free rich-text body. A null schema (the default for most categories) means a
 * category's entries are plain title-plus-body, unchanged from before.
 *
 * <p>In v1 only CHARACTER and VOICE ship a schema, seeded as system data by
 * migration V33. The mechanism extends to other categories, or to
 * author-defined categories in a future version, purely by supplying more
 * schema JSON — no code or schema change.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexSchema {

    /** Schema format version, for forward-compatible parsing. */
    @JsonProperty
    private int version;

    /** Ordered field definitions; display order is array order. */
    @JsonProperty
    private List<CodexField> fields;
}
