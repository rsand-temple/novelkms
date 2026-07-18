package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row of the author's "Reviews Received" list: a submitted review of one of
 * their packages, with the reviewer's public identity and the feedback itself.
 *
 * <p>Only SUBMITTED reviews appear here. A DRAFT is private to its reviewer
 * (spec §10) and a WITHDRAWN one has been retracted; neither is feedback the
 * author is entitled to read.
 *
 * <p>Unlike the reviewer-facing rows this <em>does</em> carry {@code contentHtml}
 * — reading the feedback is the entire point of the surface. What it never carries
 * is the reviewer's raw {@code reviewerUserId}: the reviewer is identified by
 * {@code reviewerHandle} only, exactly as authors are to reviewers, so the two
 * sides of the network see each other through the same public-identity keyhole.
 *
 * <p>{@code read} is derived from the review's {@code author_read_at} marker and
 * drives the unread badge (this slice's only notification). {@code aiAssisted} is
 * the reviewer's self-disclosure, surfaced so the author can weigh it.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReceived {

    /** The review id — the key the author uses to mark this feedback read. */
    @JsonProperty
    private UUID reviewId;

    /** The request the feedback is about — lets the UI group by package. */
    @JsonProperty
    private UUID requestId;

    @JsonProperty
    private String requestTitle;

    // ---- Reviewer public identity (never the raw user id) -------------------

    @JsonProperty
    private String reviewerHandle;

    @JsonProperty
    private String reviewerDisplayName;

    // ---- Package metadata ---------------------------------------------------

    @JsonProperty
    private String sourceTitle;

    @JsonProperty
    private String bookTitle;

    // ---- The feedback -------------------------------------------------------

    /** The review body. Present here — reading it is the point. */
    @JsonProperty
    private String contentHtml;

    @JsonProperty
    private int wordCount;

    @JsonProperty
    private boolean aiAssisted;

    @JsonProperty
    private Instant submittedAt;

    /** Whether the author has opened this feedback before. Drives the unread badge. */
    @JsonProperty
    private boolean read;
}
