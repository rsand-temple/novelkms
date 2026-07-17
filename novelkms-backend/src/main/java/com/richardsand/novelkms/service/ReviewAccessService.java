package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewQueueDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.review.ReviewPackage;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewQueueEntry;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewSnapshot;

/**
 * The reviewer's read side of the human-review network: the queue, a package view,
 * and the frozen snapshot reader.
 *
 * <p><b>This is the first legitimate cross-user read path in NovelKMS, and its
 * authorization lives here — nowhere else.</b> {@code TenantAuthorizationFilter}
 * walks path UUIDs and denies anything the caller does not own, but its segment
 * switch returns {@code default -> true}, so {@code /api/review/...} passes through
 * it untouched. That is by design: the tenant filter must keep guaranteeing that
 * the live manuscript stays private, while this service decides — explicitly, per
 * request — whether one specific frozen snapshot may be shown to one specific other
 * user. {@code ReviewPublishService} owns the author's own-content reads; this owns
 * the stranger's.
 *
 * <p><b>A denied read is 404, never 403.</b> A reviewer who asks for a package that
 * is not open to them learns only that there is nothing there — not that it exists,
 * nor who owns it. That mirrors the tenant filter's own rule and keeps the two
 * authorization layers telling the caller the same non-story.
 *
 * <p>Participation is gated on a handle (§14: the handle is the gate for all
 * participation). Blocking is honored in both directions (§21). A suspended author's
 * packages leave public view, and a suspended viewer cannot browse at all.
 */
public class ReviewAccessService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT     = 50;

    private final ReviewRequestDao  requestDao;
    private final ReviewSnapshotDao snapshotDao;
    private final ReviewProfileDao  profileDao;
    private final ReviewQueueDao    queueDao;
    private final UserBlockDao      blockDao;

    public ReviewAccessService(ReviewRequestDao requestDao,
            ReviewSnapshotDao snapshotDao,
            ReviewProfileDao profileDao,
            ReviewQueueDao queueDao,
            UserBlockDao blockDao) {
        this.requestDao = requestDao;
        this.snapshotDao = snapshotDao;
        this.profileDao = profileDao;
        this.queueDao = queueDao;
        this.blockDao = blockDao;
    }

    // =========================================================================
    // Queue
    // =========================================================================

    /**
     * A page of the public queue for the viewer. Every exclusion — own requests,
     * non-open, invite-only, capped, past-close, blocked, suspended author — is
     * applied in the DAO's SQL; this method only gates participation and normalizes
     * the filters.
     */
    public List<ReviewQueueEntry> queue(UUID viewerUserId, String genre, Integer minWords,
            Integer maxWords, String sort, Integer limit, Integer offset) throws SQLException {

        requireActiveProfile(viewerUserId);

        int lim = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        int off = offset == null ? 0 : Math.max(0, offset);

        return queueDao.findOpenQueue(viewerUserId, trimToNull(genre),
                minWords, maxWords, normalizeSort(sort), lim, off);
    }

    // =========================================================================
    // Package view
    // =========================================================================

    /**
     * The package a reviewer sees before reading: request metadata, the author's
     * public identity, and snapshot metadata — but not the frozen text, which is
     * fetched separately. Authorized as a cross-user read; 404 if the viewer may not
     * see it.
     */
    public ReviewPackage viewPackage(UUID viewerUserId, UUID requestId) throws SQLException {
        requireActiveProfile(viewerUserId);

        ReviewRequest request = authorizeRead(viewerUserId, requestId);

        // Metadata only — the package view never loads content_html.
        ReviewSnapshot snapshot = snapshotDao.findMetaByRequestIds(List.of(requestId)).get(requestId);
        ReviewProfile  author   = profileDao.findByUserId(request.getAuthorUserId()).orElse(null);

        return ReviewPackage.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .authorQuestions(request.getAuthorQuestions())
                .genre(request.getGenre())
                .feedbackTypes(request.getFeedbackTypes())
                .contentWarnings(request.getContentWarnings())
                .sourceScope(request.getSourceScope())
                .publishedAt(request.getPublishedAt())
                .closesAt(request.getClosesAt())
                .authorHandle(author == null ? null : author.getHandle())
                .authorDisplayName(author == null ? null : author.getDisplayName())
                .sourceTitle(snapshot == null ? null : snapshot.getSourceTitle())
                .bookTitle(snapshot == null ? null : snapshot.getBookTitle())
                .projectTitle(snapshot == null ? null : snapshot.getProjectTitle())
                .wordCount(snapshot == null ? 0 : snapshot.getWordCount())
                .snapshotCreatedAt(snapshot == null ? null : snapshot.getCreatedAt())
                .reviewCount(queueDao.countSubmittedReviews(requestId))
                .maxReviews(request.getMaxReviews())
                .build();
    }

    // =========================================================================
    // Snapshot reader
    // =========================================================================

    /** The frozen text, with content. Same cross-user authorization as the package view. */
    public ReviewSnapshot snapshot(UUID viewerUserId, UUID requestId) throws SQLException {
        requireActiveProfile(viewerUserId);
        authorizeRead(viewerUserId, requestId);

        return snapshotDao.findByRequestId(requestId)
                .orElseThrow(ReviewAccessService::notFound);
    }

    // =========================================================================
    // Authorization
    // =========================================================================

    /**
     * The one gate every cross-user read passes through. Returns the request if the
     * viewer may read it, and throws 404 — never 403 — if not.
     *
     * <p>The author always reads their own package, in any status. For everyone else
     * the package must be OPEN and PUBLIC, there must be no block in either direction,
     * and the author must not be suspended. Any failure is indistinguishable from the
     * package not existing.
     */
    private ReviewRequest authorizeRead(UUID viewerUserId, UUID requestId) throws SQLException {
        ReviewRequest request = requestDao.findById(requestId).orElseThrow(ReviewAccessService::notFound);

        if (request.getAuthorUserId().equals(viewerUserId)) {
            return request;
        }

        boolean openToPublic = ReviewRequestDao.STATUS_OPEN.equals(request.getStatus())
                && ReviewRequestDao.VISIBILITY_PUBLIC.equals(request.getVisibility());
        if (!openToPublic) {
            throw notFound();
        }

        if (blockDao.blockedBetween(viewerUserId, request.getAuthorUserId())) {
            throw notFound();
        }

        ReviewProfile author = profileDao.findByUserId(request.getAuthorUserId()).orElse(null);
        if (author == null || ReviewProfileDao.STATUS_SUSPENDED.equals(author.getStatus())) {
            throw notFound();
        }

        return request;
    }

    /**
     * Participation requires a handle (§14), and a suspended account cannot
     * participate at all (§21). The suspension answer is about the viewer's own
     * account, so 403 is honest here — unlike a cross-user read, there is nothing to
     * conceal from the viewer about themselves.
     */
    private ReviewProfile requireActiveProfile(UUID viewerUserId) throws SQLException {
        ReviewProfile profile = profileDao.findByUserId(viewerUserId)
                .orElseThrow(() -> new ReviewException(409, "profile_required",
                        "Claim a handle before taking part in the review community."));

        if (ReviewProfileDao.STATUS_SUSPENDED.equals(profile.getStatus())) {
            throw new ReviewException(403, "suspended",
                    "Your review-network access is suspended.");
        }
        return profile;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static ReviewException notFound() {
        return new ReviewException(404, "not_found", "No such review package.");
    }

    private static String normalizeSort(String sort) {
        if (ReviewQueueDao.SORT_OLDEST.equalsIgnoreCase(sort)) {
            return ReviewQueueDao.SORT_OLDEST;
        }
        if (ReviewQueueDao.SORT_FEWEST.equalsIgnoreCase(sort)) {
            return ReviewQueueDao.SORT_FEWEST;
        }
        return ReviewQueueDao.SORT_NEWEST;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
