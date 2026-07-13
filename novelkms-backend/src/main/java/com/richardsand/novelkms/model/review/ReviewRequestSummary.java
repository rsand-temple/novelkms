package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row of the author's My Requests list: a {@link ReviewRequest} plus the
 * snapshot metadata the author needs to recognize it, and the derived source
 * state.
 *
 * <p>It exists so the list read never loads {@code contentHtml} — a page of a
 * dozen requests would otherwise drag a dozen whole chapters across the wire to
 * render some titles and word counts.
 *
 * <p>{@code sourceState} is deliberately a string rather than a pair of booleans
 * ({@code sourceDeleted} / {@code sourceChanged}): the states are mutually
 * exclusive, two booleans would admit a nonsense fourth combination, and the
 * Lombok/Jackson is-prefix rule makes boolean fields a hazard here anyway.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestSummary {

    /** The source chapter is still in the manuscript and unchanged since publication. */
    public static final String SOURCE_CURRENT = "CURRENT";

    /** The source chapter still exists but has been edited since the snapshot was taken. */
    public static final String SOURCE_CHANGED = "CHANGED";

    /** The source chapter has been trashed or deleted. The snapshot is unaffected. */
    public static final String SOURCE_DELETED = "DELETED";

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String title;

    @JsonProperty
    private String description;

    @JsonProperty
    private String genre;

    @JsonProperty
    private List<String> feedbackTypes;

    @JsonProperty
    private String visibility;

    @JsonProperty
    private String status;

    @JsonProperty
    private Instant publishedAt;

    @JsonProperty
    private Instant closesAt;

    @JsonProperty
    private Instant closedAt;

    @JsonProperty
    private Instant updatedAt;

    // ---- Frozen snapshot metadata -------------------------------------------

    @JsonProperty
    private String sourceTitle;

    @JsonProperty
    private String bookTitle;

    @JsonProperty
    private int wordCount;

    @JsonProperty
    private Instant snapshotCreatedAt;

    // ---- Derived ------------------------------------------------------------

    /** CURRENT, CHANGED, or DELETED — see the constants above. */
    @JsonProperty
    private String sourceState;

    /**
     * Submitted reviews against this request. Always zero until slice 1D, when
     * reviews can first be written; the field exists now so the My Requests card
     * does not have to change shape later.
     */
    @JsonProperty
    private int reviewCount;
}
