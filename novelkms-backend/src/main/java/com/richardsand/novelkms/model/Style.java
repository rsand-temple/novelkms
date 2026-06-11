package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A named paragraph style at some scope.
 *
 * <p>{@code styleKey} is one of the fixed roster (StyleDefaults.STYLE_KEYS).
 * {@code scope} is GLOBAL / PROJECT / BOOK. {@code projectId} is set for
 * PROJECT rows, {@code bookId} for BOOK rows; both null for GLOBAL.
 *
 * <p>When returned from a resolve endpoint, inspect {@code scope} to know which
 * level the effective definition came from (i.e. whether the project/book is
 * overriding or inheriting).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Style {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String styleKey;

    @JsonProperty
    private String scope;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private StyleDefinition definition;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
