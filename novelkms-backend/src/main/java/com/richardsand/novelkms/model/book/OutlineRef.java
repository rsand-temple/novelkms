package com.richardsand.novelkms.model.book;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A typed reference to one entry in a book's outline sequence.
 *
 * <p>Because the outline is a union of two tables, an ID alone is ambiguous at
 * the point of writing {@code display_order} back — the caller must say which
 * table the row lives in. Every outline reorder/move payload is therefore a
 * list of these rather than a bare list of UUIDs.
 *
 * <p>The {@code id} field name matters: {@code TenantAuthorizationFilter}
 * ownership-checks any JSON property named {@code id} / {@code *Id} / {@code *Ids}
 * on request bodies whose path ends in {@code /reorder} or {@code /move}, so the
 * items in these payloads are authorized for free.
 */
public record OutlineRef(
        @JsonProperty("type") OutlineItemType type,
        @JsonProperty("id") UUID id) {
}
