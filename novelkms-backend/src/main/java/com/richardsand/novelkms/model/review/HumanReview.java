package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One reviewer's review of one package ({@code human_review}) — the mutable half
 * of the reviewer's contribution, DRAFT while being written and immutable in
 * effect once SUBMITTED.
 *
 * <p>The {@code UNIQUE(request_id, reviewer_user_id)} constraint means a reviewer
 * has at most one review per package. That single row carries its own lifecycle:
 * it starts DRAFT, becomes SUBMITTED when delivered to the author, and can be
 * WITHDRAWN. There is no in-place edit of a submitted review (spec §30.2 Q6):
 * revising means moving the row back to DRAFT and resubmitting, and the one row
 * makes "withdraw and rewrite" fall out for free rather than needing a second row.
 *
 * <p>This object is only ever returned to the reviewer for their <em>own</em>
 * review (the package's write endpoints). The author's view of received feedback
 * is {@link ReviewReceived}, and the reviewer's list of their own work is
 * {@link ReviewWritingSummary}; both carry handles, never raw user ids. So the
 * internal ids here are kept off the wire:
 *
 * <ul>
 *   <li>{@code reviewerUserId} — the caller's own id; harmless but unneeded, and
 *       omitting it keeps the "no user ids on the review-network wire" rule
 *       uniform.</li>
 *   <li>{@code snapshotId} — the frozen text the review is bound to; an internal
 *       linkage the frontend never uses.</li>
 *   <li>{@code authorReadAt} — the author's private read-state, which powers the
 *       Reviews Received badge. A reviewer must not be able to infer whether or
 *       when the author opened their feedback.</li>
 * </ul>
 *
 * <p>As with the other review DTOs, {@code @JsonIgnore} — not the mere absence of
 * {@code @JsonProperty} — is what keeps a Lombok getter off the wire, and there
 * are no {@code isX} boolean fields ({@code aiAssisted} is a plain boolean, read
 * through {@code isAiAssisted()} but named without the {@code is} prefix as a
 * field).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReview {

    @JsonProperty
    private UUID id;

    /** The request being reviewed. Harmless to expose — the reviewer already has it. */
    @JsonProperty
    private UUID requestId;

    /** The frozen text this review is permanently bound to. Internal. */
    @JsonIgnore
    private UUID snapshotId;

    /** The reviewer. Internal — the caller is always themselves here. */
    @JsonIgnore
    private UUID reviewerUserId;

    /** DRAFT, SUBMITTED, or WITHDRAWN (REMOVED is admin-only, slice 1F). */
    @JsonProperty
    private String status;

    /** PRIVATE in Phase 1. PUBLIC review reading is a Phase 2 surface. */
    @JsonProperty
    private String visibility;

    /** The review body. Plain text wrapped in paragraphs; frozen wording once submitted. */
    @JsonProperty
    private String contentHtml;

    /** Recomputed from {@code contentHtml} on every write — the 1E metric input. */
    @JsonProperty
    private int wordCount;

    /** Reviewer self-disclosure that AI assisted the review (spec §30.2 Q15/Q16). */
    @JsonProperty
    private boolean aiAssisted;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;

    @JsonProperty
    private Instant submittedAt;

    @JsonProperty
    private Instant withdrawnAt;

    /** The author's private read-state marker. Never serialized. */
    @JsonIgnore
    private Instant authorReadAt;
}
