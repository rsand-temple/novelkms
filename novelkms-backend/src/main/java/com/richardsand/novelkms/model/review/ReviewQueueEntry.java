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
 * One row of the public review queue (spec §12): enough for a reviewer to decide
 * whether to open a package, and nothing more.
 *
 * <p>This is the reviewer-facing mirror of {@link ReviewRequestSummary}, and the
 * differences are deliberate. It carries the author's <em>handle</em>, never their
 * {@code authorUserId}; and it never carries {@code sourceEntityId}. The queue is
 * cross-user read surface — the first in NovelKMS — so it exposes the public review
 * identity and not one byte of the manuscript's internal shape. A reviewer has no
 * use for a chapter UUID, and leaking it would hand them a live handle into the
 * author's private tree.
 *
 * <p>{@code id} is the request id — the handle a reviewer uses to open the package
 * and, later, to submit a review.
 *
 * <p>{@code reviewCount} is the count of submitted reviews already received. It is
 * always zero until slice 1D, when reviews can first be written; the field exists
 * now so the queue card does not have to change shape later. {@code maxReviews} is
 * the author's optional cap — a request already at its cap never reaches the queue
 * (the query excludes it), so a reviewer never opens a package they cannot help.
 *
 * <p>No {@code isX} boolean fields, per the Lombok/Jackson is-prefix collision rule.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQueueEntry {

    /** The review request id — the key to open the package and submit a review. */
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

    /** Shown before a reviewer opens the package, per §12 — hence on the queue row. */
    @JsonProperty
    private String contentWarnings;

    /** CHAPTER in Phase 1. */
    @JsonProperty
    private String sourceScope;

    /** From the frozen snapshot, so it is exactly the length a reviewer will read. */
    @JsonProperty
    private int wordCount;

    @JsonProperty
    private Instant publishedAt;

    /** The author's public handle. Never the raw user id. */
    @JsonProperty
    private String authorHandle;

    /** Optional friendly name; the UI falls back to the handle when absent. */
    @JsonProperty
    private String authorDisplayName;

    /** Submitted reviews already received. Zero until slice 1D. */
    @JsonProperty
    private int reviewCount;

    /** Optional author cap. A request at its cap is excluded from the queue upstream. */
    @JsonProperty
    private Integer maxReviews;
}
