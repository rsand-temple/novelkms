package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewReceived;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewSnapshot;
import com.richardsand.novelkms.model.review.ReviewWritingSummary;
import com.richardsand.novelkms.utils.WordCount;

/**
 * Writing, submitting, and receiving reviews — the second cross-user path in the
 * human-review network, and the first cross-user <em>write</em>.
 *
 * <p>Where {@code ReviewAccessService} decides whether a stranger may <em>read</em>
 * a package and {@code ReviewPublishService} owns the author's own request
 * lifecycle, this service owns everything that touches {@code human_review}: the
 * reviewer composing and submitting feedback, and the author reading what came
 * back. Keeping all of that in one place keeps the review status machine and the
 * writability rules from being reinvented per endpoint.
 *
 * <p><b>Two distinct gates, because "paused" means different things to different
 * reviewers.</b> A request that is PAUSED is, per spec §9, unavailable to
 * <em>new</em> reviewers but not a dead end for someone mid-review:
 *
 * <ul>
 *   <li>{@link #ensureCanStart} — OPEN + PUBLIC, author ACTIVE, not the caller's
 *       own, no block: the gate for creating a review that does not exist yet.</li>
 *   <li>{@link #ensureCanWrite} — OPEN or PAUSED, author ACTIVE, no block: the gate
 *       for saving or submitting a review that already exists, so a reviewer can
 *       finish what a pause interrupted but cannot slip a fresh one in.</li>
 * </ul>
 *
 * Once CLOSED or WITHDRAWN, the reviewer keeps read access to their own review and
 * may still withdraw it (§30.2 Q5/Q7), but cannot save or submit.
 *
 * <p><b>Every denial is 404, never 403</b> for anything cross-user — the same
 * non-story the read side tells. The one 403 is a suspended caller learning about
 * their <em>own</em> account, where there is nothing to conceal.
 */
public class HumanReviewService {

    private final ReviewRequestDao  requestDao;
    private final ReviewSnapshotDao snapshotDao;
    private final ReviewProfileDao  profileDao;
    private final HumanReviewDao    reviewDao;
    private final UserBlockDao      blockDao;

    public HumanReviewService(ReviewRequestDao requestDao,
            ReviewSnapshotDao snapshotDao,
            ReviewProfileDao profileDao,
            HumanReviewDao reviewDao,
            UserBlockDao blockDao) {
        this.requestDao = requestDao;
        this.snapshotDao = snapshotDao;
        this.profileDao = profileDao;
        this.reviewDao = reviewDao;
        this.blockDao = blockDao;
    }

    // =========================================================================
    // Reviewer — read own review
    // =========================================================================

    /**
     * The caller's own review of a package, if they have one. Returns empty (not an
     * error) when they have not started one but the package is reviewable, so the
     * editor can offer to begin. 404 only when the request does not exist or is not
     * reviewable and they have no review to fall back on — the caller learns nothing
     * about a package they may not touch.
     */
    public Optional<HumanReview> myReview(UUID reviewerUserId, UUID requestId) throws SQLException {
        requireActiveProfile(reviewerUserId);

        ReviewRequest request = requestDao.findById(requestId).orElseThrow(HumanReviewService::notFound);

        Optional<HumanReview> existing = reviewDao.findByRequestAndReviewer(requestId, reviewerUserId);
        if (existing.isPresent()) {
            return existing;
        }

        // No review yet: only confirm they could start one if the package is really
        // open to them. Otherwise this is indistinguishable from the package not existing.
        ensureCanStart(reviewerUserId, request);
        return Optional.empty();
    }

    // =========================================================================
    // Reviewer — save / submit / withdraw
    // =========================================================================

    /**
     * Saves the caller's DRAFT review, creating it on first save. Content may be
     * blank while drafting. Whatever state the row was in, this returns it to DRAFT
     * (that is what makes revising a submission, or rewriting a withdrawal, a single
     * rule). Visibility is PRIVATE in Phase 1 and not caller-controllable.
     */
    public HumanReview saveDraft(UUID reviewerUserId, UUID requestId, String contentHtml, boolean aiAssisted)
            throws SQLException {

        requireActiveProfile(reviewerUserId);
        ReviewRequest request = requestDao.findById(requestId).orElseThrow(HumanReviewService::notFound);

        String html  = contentHtml == null ? "" : contentHtml;
        int    words = WordCount.fromHtml(html);

        Optional<HumanReview> existing = reviewDao.findByRequestAndReviewer(requestId, reviewerUserId);
        if (existing.isEmpty()) {
            ensureCanStart(reviewerUserId, request);
            ReviewSnapshot snapshot = snapshotDao.findByRequestId(requestId)
                    .orElseThrow(HumanReviewService::notFound);
            return reviewDao.insert(requestId, snapshot.getId(), reviewerUserId,
                    html, words, HumanReviewDao.VISIBILITY_PRIVATE, aiAssisted);
        }

        ensureCanWrite(reviewerUserId, request);
        return reviewDao.saveContent(existing.get().getId(), reviewerUserId,
                html, words, HumanReviewDao.VISIBILITY_PRIVATE, aiAssisted)
                .orElseThrow(HumanReviewService::notFound);
    }

    /**
     * Delivers the caller's review to the author. Saves the supplied content first,
     * so submit is atomic whether or not a draft was saved beforehand, then moves
     * DRAFT -> SUBMITTED. A submitted review must actually say something (400
     * {@code empty_review}), and the author's optional {@code max_reviews} cap is
     * enforced here (409 {@code cap_reached}) — the queue excludes capped requests
     * from new reviewers, but a reviewer who opened before the cap filled is stopped
     * at the last honest moment.
     */
    public HumanReview submit(UUID reviewerUserId, UUID requestId, String contentHtml, boolean aiAssisted)
            throws SQLException {

        requireActiveProfile(reviewerUserId);
        ReviewRequest request = requestDao.findById(requestId).orElseThrow(HumanReviewService::notFound);

        String html = contentHtml == null ? "" : contentHtml;
        if (html.isBlank() || WordCount.fromHtml(html) == 0) {
            throw new ReviewException(400, "empty_review", "A review needs some words before it can be submitted.");
        }

        Optional<HumanReview> existing = reviewDao.findByRequestAndReviewer(requestId, reviewerUserId);

        UUID reviewId;
        if (existing.isEmpty()) {
            ensureCanStart(reviewerUserId, request);
            ReviewSnapshot snapshot = snapshotDao.findByRequestId(requestId)
                    .orElseThrow(HumanReviewService::notFound);
            reviewId = reviewDao.insert(requestId, snapshot.getId(), reviewerUserId,
                    html, WordCount.fromHtml(html), HumanReviewDao.VISIBILITY_PRIVATE, aiAssisted).getId();
        } else {
            ensureCanWrite(reviewerUserId, request);
            reviewId = reviewDao.saveContent(existing.get().getId(), reviewerUserId,
                    html, WordCount.fromHtml(html), HumanReviewDao.VISIBILITY_PRIVATE, aiAssisted)
                    .orElseThrow(HumanReviewService::notFound).getId();
        }

        // Cap is measured against the reviews already SUBMITTED — this draft is not
        // among them until the submit below, so a full cap blocks it cleanly.
        Integer cap = request.getMaxReviews();
        if (cap != null && reviewDao.countSubmitted(requestId) >= cap) {
            throw new ReviewException(409, "cap_reached",
                    "This request has reached the number of reviews the author asked for.");
        }

        return reviewDao.submit(reviewId, reviewerUserId, Instant.now())
                .orElseThrow(HumanReviewService::notFound);
    }

    /**
     * Retracts the caller's review (spec §30.2 Q7). Idempotent: an already-withdrawn
     * review is returned unchanged. The row is retained, never deleted — records
     * must survive for dispute handling (§30.2 Q8), and an author must not be able to
     * make feedback vanish. A withdrawn review can be rewritten by saving it again,
     * which returns it to DRAFT.
     */
    public HumanReview withdraw(UUID reviewerUserId, UUID requestId) throws SQLException {
        requireActiveProfile(reviewerUserId);

        HumanReview existing = reviewDao.findByRequestAndReviewer(requestId, reviewerUserId)
                .orElseThrow(HumanReviewService::notFound);

        if (HumanReviewDao.STATUS_WITHDRAWN.equals(existing.getStatus())) {
            return existing;
        }
        // No request-status gate: a reviewer may retract their own review whatever
        // has become of the request.
        return reviewDao.withdraw(existing.getId(), reviewerUserId, Instant.now())
                .orElseThrow(HumanReviewService::notFound);
    }

    // =========================================================================
    // Reviewer — "Reviews I'm Writing"
    // =========================================================================

    public List<ReviewWritingSummary> writing(UUID reviewerUserId) throws SQLException {
        requireActiveProfile(reviewerUserId);
        return reviewDao.findWritingByReviewer(reviewerUserId);
    }

    // =========================================================================
    // Author — "Reviews Received"
    // =========================================================================

    public List<ReviewReceived> received(UUID authorUserId) throws SQLException {
        requireActiveProfile(authorUserId);
        return reviewDao.findReceivedByAuthor(authorUserId);
    }

    /** The unread-feedback count that badges the Reviews Received tab. */
    public int unreadReceivedCount(UUID authorUserId) throws SQLException {
        requireActiveProfile(authorUserId);
        return reviewDao.countUnreadForAuthor(authorUserId);
    }

    /**
     * Marks one received review read. Authorized by request ownership — a review
     * against someone else's request is 404, the same non-story every other
     * cross-user miss tells. Idempotent: opening an already-read review is fine.
     */
    public void markReceivedRead(UUID authorUserId, UUID reviewId) throws SQLException {
        requireActiveProfile(authorUserId);

        UUID owner = reviewDao.findRequestAuthor(reviewId).orElseThrow(HumanReviewService::notFound);
        if (!owner.equals(authorUserId)) {
            throw notFound();
        }
        reviewDao.markAuthorRead(reviewId, authorUserId);
    }

    // =========================================================================
    // Authorization
    // =========================================================================

    /**
     * The gate for starting a review that does not exist yet: the package must be
     * genuinely open to the caller. Mirrors {@code ReviewAccessService.authorizeRead}
     * for a non-author, and adds the self-review block — an author reviewing their
     * own package is meaningless and would poison their own metrics.
     */
    private void ensureCanStart(UUID reviewerUserId, ReviewRequest request) throws SQLException {
        if (request.getAuthorUserId().equals(reviewerUserId)) {
            throw new ReviewException(400, "own_request", "You cannot review your own package.");
        }

        boolean openToPublic = ReviewRequestDao.STATUS_OPEN.equals(request.getStatus())
                && ReviewRequestDao.VISIBILITY_PUBLIC.equals(request.getVisibility());
        if (!openToPublic) {
            throw notFound();
        }
        ensureAuthorVisible(reviewerUserId, request);
    }

    /**
     * The gate for saving or submitting a review that already exists: OPEN or
     * PAUSED, so a reviewer can finish through a pause but the window is genuinely
     * shut once the author closes or withdraws the request. Self-review is already
     * impossible here — the caller holds a review, which they could only have
     * started on someone else's package.
     */
    private void ensureCanWrite(UUID reviewerUserId, ReviewRequest request) throws SQLException {
        boolean writable = ReviewRequestDao.STATUS_OPEN.equals(request.getStatus())
                || ReviewRequestDao.STATUS_PAUSED.equals(request.getStatus());
        if (!writable) {
            throw new ReviewException(409, "request_not_open",
                    "This request is no longer accepting reviews.");
        }
        ensureAuthorVisible(reviewerUserId, request);
    }

    /** No block in either direction, and the author is not suspended. Else 404. */
    private void ensureAuthorVisible(UUID reviewerUserId, ReviewRequest request) throws SQLException {
        if (blockDao.blockedBetween(reviewerUserId, request.getAuthorUserId())) {
            throw notFound();
        }
        ReviewProfile author = profileDao.findByUserId(request.getAuthorUserId()).orElse(null);
        if (author == null || ReviewProfileDao.STATUS_SUSPENDED.equals(author.getStatus())) {
            throw notFound();
        }
    }

    /**
     * Participation requires a handle (§14) and a non-suspended account (§21). The
     * suspension answer is about the caller's own account, so 403 is honest here.
     */
    private ReviewProfile requireActiveProfile(UUID userId) throws SQLException {
        ReviewProfile profile = profileDao.findByUserId(userId)
                .orElseThrow(() -> new ReviewException(409, "profile_required",
                        "Claim a handle before taking part in the review community."));

        if (ReviewProfileDao.STATUS_SUSPENDED.equals(profile.getStatus())) {
            throw new ReviewException(403, "suspended", "Your review-network access is suspended.");
        }
        return profile;
    }

    private static ReviewException notFound() {
        return new ReviewException(404, "not_found", "No such review package.");
    }
}
