package com.richardsand.novelkms.model.codex;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A Codex is a Part-like container for world-building material. It belongs to
 * exactly one project (series-wide) or one book — exactly one of projectId /
 * bookId is set. Its category "chapters" and entry "scenes" reuse the existing
 * chapter/scene tables; codex chapters carry codex_id and have book_id NULL.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Codex {

    @JsonProperty
    private UUID id;

    /** Set when the codex is series-wide (project scope); null for a book-scoped codex. */
    @JsonProperty
    private UUID projectId;

    /** Set when the codex is book-scoped; null for a project-scoped codex. */
    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private String title;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
