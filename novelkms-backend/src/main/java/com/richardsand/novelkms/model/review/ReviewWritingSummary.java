package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row of the reviewer's "Reviews I'm Writing" list: their own review joined to
 * the package it is against, with just enough to recognize the work and resume,
 * submit, or withdraw it.
 *
 * <p>The list shows a reviewer's active reviews — DRAFT and SUBMITTED — with a
 * status chip, so it is the single place they see both drafts they can finish and
 * submissions they can withdraw. WITHDRAWN and REMOVED rows are excluded; a
 * retracted review is not work in progress.
 *
 * <p>Like every reviewer-facing row this carries the author's <em>handle</em>,
 * never their {@code authorUserId}, and never the manuscript's
 * {@code sourceEntityId}. It deliberately does not carry the review body — the
 * list renders from metadata, and the body is loaded on demand when the reviewer
 * reopens the package to edit.
 *
 * <p>{@code requestStatus} is included so the card can tell the reviewer when the
 * window has moved on — a package that has been PAUSED, CLOSED, or WITHDRAWN by
 * its author — and disable Submit accordingly, matching what the service would
 * enforce anyway.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewWritingSummary {

    /** The review id — the caller's own review of this package. */
    @JsonProperty
    private UUID reviewId;

    /** The request id — the key to reopen the package and continue the review. */
    @JsonProperty
    private UUID requestId;

    @JsonProperty
    private String requestTitle;

    /** The author's public handle. Never the raw user id. */
    @JsonProperty
    private String authorHandle;

    @JsonProperty
    private String authorDisplayName;

    // ---- Frozen snapshot metadata (no content) ------------------------------

    @JsonProperty
    private String sourceTitle;

    @JsonProperty
    private String bookTitle;

    /** The snapshot's word count — the length the reviewer is reading. */
    @JsonProperty
    private int snapshotWordCount;

    // ---- The review ---------------------------------------------------------

    /** DRAFT or SUBMITTED. */
    @JsonProperty
    private String status;

    /** The review's own word count. */
    @JsonProperty
    private int wordCount;

    @JsonProperty
    private boolean aiAssisted;

    @JsonProperty
    private Instant updatedAt;

    @JsonProperty
    private Instant submittedAt;

    // ---- The package's current lifecycle state ------------------------------

    /** OPEN / PAUSED / CLOSED / WITHDRAWN — so the card can gate Submit honestly. */
    @JsonProperty
    private String requestStatus;
}
