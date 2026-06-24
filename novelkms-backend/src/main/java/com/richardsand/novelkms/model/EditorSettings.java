package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One {@code editor_settings} row at some scope, carrying a resolved or stored
 * {@link EditorSettingsDefinition}.
 *
 * <p>{@code scope} is {@code SYSTEM | USER | PROJECT | BOOK}. {@code projectId}
 * is set for PROJECT rows and {@code bookId} for BOOK rows; both are null for
 * SYSTEM and USER rows.
 *
 * <p>When returned from a resolve endpoint, inspect {@code scope} to know which
 * level the effective definition came from — i.e. whether the book is overriding
 * ({@code BOOK}), the project is overriding ({@code PROJECT}), or it is
 * inheriting the user default ({@code USER}) or the factory default
 * ({@code SYSTEM}). Mirrors the {@link Style} model so the frontend
 * override-badge logic is identical.
 *
 * <p>{@code user_id} is intentionally not serialized (the row is always returned
 * to its owner), matching {@link Style}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditorSettings {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String scope;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private EditorSettingsDefinition definition;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
