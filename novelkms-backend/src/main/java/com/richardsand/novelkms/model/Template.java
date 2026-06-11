package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A layout template for a generated page (cover page, part page, ...).
 *
 * <p>{@code templateType} is one of {@code COVER} / {@code PART}.
 * {@code scope} is {@code GLOBAL} (the editable default that spans all
 * projects) or {@code BOOK} (a per-book override). For GLOBAL rows
 * {@code bookId} is {@code null}; for BOOK rows it identifies the owning book.
 *
 * <p>{@code content} is TipTap HTML and may contain field tokens rendered as
 * {@code <span data-token="TITLE"></span>} etc., resolved at render time
 * against the book / project.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String templateType;

    @JsonProperty
    private String scope;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private String content;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
