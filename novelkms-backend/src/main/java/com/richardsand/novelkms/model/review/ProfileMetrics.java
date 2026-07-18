package com.richardsand.novelkms.model.review;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A user's public contribution figures in the human-review network (spec §13).
 *
 * <p>This is the "separate response object" the {@link ReviewProfile} docstring
 * promises for slice 1E: metrics are <em>derived at read time</em> and never
 * stored on the profile. Two figures are pure aggregates over that user's
 * SUBMITTED reviews and are self-deduping by construction — {@code human_review}
 * carries {@code UNIQUE (request_id, reviewer_user_id)}, so a package can be
 * opened and redrafted any number of times yet contributes to
 * {@link #wordsReviewed} at most once, at the moment its review reaches
 * SUBMITTED. Withdrawing a review drops it from every figure automatically. No
 * read log, view event, or counter column is involved.
 *
 * <p><b>The figures are objective, not viewer-relative.</b> Spec §6.5 requires
 * public metrics to be objective, so they are the profile owner's true totals
 * and read identically for every viewer. In particular they are <em>not</em>
 * block-filtered: unlike the Reviews Received <em>list</em> — which hides a
 * blocked counterparty in both directions — {@link #reviewsReceived} counts
 * every submitted review against the owner's requests. Block enforcement is a
 * disclosure concern that lives on list surfaces and on profile visibility
 * (Phase 1F), never inside an aggregate that is meant to be stable.
 *
 * <p><b>Deferred in 1E.</b> The public/private split (§13.1) is intentionally
 * absent: every submitted review is PRIVATE in Phase 1 — {@code HumanReviewService.submit}
 * hardcodes it — so a "public reviews written" figure would report zero for
 * everyone until a later slice lets reviews be published. "Recent review
 * activity" is likewise deferred; it needs a dated surface and would leak a
 * contributor's cadence.
 *
 * <p>No {@code isX} boolean fields, per the Lombok/Jackson is-prefix collision
 * rule. Word-count sums are {@code long} because they aggregate across a user's
 * whole history; the review/received counts are per-user tallies and fit an int.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMetrics {

    /** Whose figures these are. Echoes the profile handle the caller asked for. */
    @JsonProperty
    private String handle;

    /** SUM of snapshot word counts over the user's SUBMITTED reviews (§13.2). */
    @JsonProperty
    private long wordsReviewed;

    /** SUM of the user's own SUBMITTED review word counts (§13.2). */
    @JsonProperty
    private long reviewWordsWritten;

    /** Count of the user's SUBMITTED reviews. */
    @JsonProperty
    private int reviewsCompleted;

    /** Count of SUBMITTED reviews against this user's own requests. */
    @JsonProperty
    private int reviewsReceived;

    /** When the user claimed their handle ({@code review_profile.created_at}). */
    @JsonProperty
    private Instant memberSince;
}
