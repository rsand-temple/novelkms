package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewReceived;
import com.richardsand.novelkms.model.review.ReviewRequest;

/**
 * What a reviewer may and may not do, and what an author may read back — the write
 * side of the first cross-user path in NovelKMS.
 *
 * <p>The load-bearing rules pinned here:
 *
 * <ul>
 *   <li>You cannot review your own package, and every other denied write is a 404,
 *       never a 403 that would confirm a package exists.</li>
 *   <li>A new review needs an OPEN, PUBLIC package; an existing draft can still be
 *       finished through a PAUSE but not after a CLOSE.</li>
 *   <li>Submit requires words and honors the author's cap; withdraw always works on
 *       your own review and retains the row.</li>
 *   <li>Reviews Received shows only submitted feedback, badges the unread, and the
 *       read-marker refuses a review against someone else's request.</li>
 * </ul>
 */
class HumanReviewServiceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final HumanReviewDao    REVIEWS   = new HumanReviewDao(ds);
    private static final UserBlockDao      BLOCKS    = new UserBlockDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final HumanReviewService SERVICE = new HumanReviewService(
            REQUESTS, SNAPSHOTS, PROFILES, REVIEWS, BLOCKS);

    private static final UUID AUTHOR   = TEST_USER_ID;
    private static final UUID REVIEWER = OTHER_USER_ID;
    private static final UUID THIRD    = THIRD_USER_ID;

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(AUTHOR,   ReviewProfile.builder().handle("Author_One").displayName("Ann").build());
        PROFILES.create(REVIEWER, ReviewProfile.builder().handle("Reviewer_Two").build());
        PROFILES.create(THIRD,    ReviewProfile.builder().handle("Third_Three").build());

        Project project = createTestProject("The Long Road", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private ReviewRequest publish(String title, Integer maxReviews) throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, title, null, null);
        UUID sid = sceneDao.create(ch.getId(), "Scene", null).getId();
        sceneDao.saveContent(sid, "<p>One two three four words.</p>", 0);
        return PUBLISH.publishChapter(AUTHOR, ch.getId(),
                ReviewRequest.builder().title(title).genre("fantasy").maxReviews(maxReviews).build());
    }

    private ReviewRequest publish(String title) throws SQLException {
        return publish(title, null);
    }

    private static ReviewException expect(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReviewException.class, e);
    }

    private void insertBlock(UUID blocker, UUID blocked) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO user_block (id, blocker_user_id, blocked_user_id) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, blocker);
            ps.setObject(3, blocked);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Starting a review
    // =========================================================================

    @Test
    void author_cannotReviewOwnPackage() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        ReviewException e = expect(() -> SERVICE.saveDraft(AUTHOR, req.getId(), "<p>self</p>", false));
        assertEquals(400, e.status());
        assertEquals("own_request", e.code());
    }

    @Test
    void withoutProfile_cannotParticipate() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        // A user who never claimed a handle is gated before anything else runs.
        UUID noProfile = UUID.fromString("40000000-0000-0000-0000-000000000004");
        ReviewException e = expect(() -> SERVICE.saveDraft(noProfile, req.getId(), "<p>x</p>", false));
        assertEquals(409, e.status());
        assertEquals("profile_required", e.code());
    }

    @Test
    void suspendedReviewer_isRefused403() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        PROFILES.setStatus(REVIEWER, ReviewProfileDao.STATUS_SUSPENDED);
        ReviewException e = expect(() -> SERVICE.saveDraft(REVIEWER, req.getId(), "<p>x</p>", false));
        assertEquals(403, e.status());
        assertEquals("suspended", e.code());
    }

    @Test
    void newReview_onPausedRequest_is404() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        PUBLISH.pause(AUTHOR, req.getId());
        // A brand-new reviewer cannot start on a paused request (unavailable to new reviewers).
        ReviewException e = expect(() -> SERVICE.saveDraft(REVIEWER, req.getId(), "<p>x</p>", false));
        assertEquals(404, e.status());
    }

    @Test
    void blockedReviewer_seesNothing_404() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        insertBlock(AUTHOR, REVIEWER);
        ReviewException e = expect(() -> SERVICE.saveDraft(REVIEWER, req.getId(), "<p>x</p>", false));
        assertEquals(404, e.status());
    }

    // =========================================================================
    // Draft -> pause -> submit continuity
    // =========================================================================

    @Test
    void existingDraft_survivesPause_canStillSubmit() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.saveDraft(REVIEWER, req.getId(), "<p>Halfway through.</p>", false);

        PUBLISH.pause(AUTHOR, req.getId());
        // Saving and submitting an existing draft is allowed through a pause.
        SERVICE.saveDraft(REVIEWER, req.getId(), "<p>Finished my thoughts here.</p>", false);
        HumanReview submitted = SERVICE.submit(REVIEWER, req.getId(), "<p>Finished my thoughts here.</p>", false);
        assertEquals(HumanReviewDao.STATUS_SUBMITTED, submitted.getStatus());
    }

    @Test
    void closedRequest_blocksSaveAndSubmit_butWithdrawStillWorks() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.saveDraft(REVIEWER, req.getId(), "<p>Draft.</p>", false);
        PUBLISH.close(AUTHOR, req.getId());

        ReviewException save = expect(() -> SERVICE.saveDraft(REVIEWER, req.getId(), "<p>more</p>", false));
        assertEquals(409, save.status());
        assertEquals("request_not_open", save.code());

        ReviewException submit = expect(() -> SERVICE.submit(REVIEWER, req.getId(), "<p>more words here</p>", false));
        assertEquals(409, submit.status());

        // Withdraw of the reviewer's own review is allowed whatever became of the request.
        HumanReview wd = SERVICE.withdraw(REVIEWER, req.getId());
        assertEquals(HumanReviewDao.STATUS_WITHDRAWN, wd.getStatus());
    }

    // =========================================================================
    // Submit rules
    // =========================================================================

    @Test
    void submit_emptyReview_is400() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        ReviewException e = expect(() -> SERVICE.submit(REVIEWER, req.getId(), "   ", false));
        assertEquals(400, e.status());
        assertEquals("empty_review", e.code());
    }

    @Test
    void submit_atCap_is409() throws SQLException {
        ReviewRequest req = publish("Chapter A", 1);

        // First reviewer submits and fills the cap of 1.
        SERVICE.submit(REVIEWER, req.getId(), "<p>First review, thoughtful and long enough.</p>", false);

        // Second reviewer opened before the cap filled but is stopped at submit.
        SERVICE.saveDraft(THIRD, req.getId(), "<p>My draft.</p>", false);
        ReviewException e = expect(() -> SERVICE.submit(THIRD, req.getId(), "<p>My draft, now finished.</p>", false));
        assertEquals(409, e.status());
        assertEquals("cap_reached", e.code());
    }

    @Test
    void submit_withoutPriorSave_isAtomic() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview submitted = SERVICE.submit(REVIEWER, req.getId(), "<p>Straight to submit, no draft first.</p>", true);
        assertEquals(HumanReviewDao.STATUS_SUBMITTED, submitted.getStatus());
        assertTrue(submitted.isAiAssisted());
        assertEquals(1, REVIEWS.countSubmitted(req.getId()));
    }

    // =========================================================================
    // myReview
    // =========================================================================

    @Test
    void myReview_empty_whenReviewableButNotStarted() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        assertTrue(SERVICE.myReview(REVIEWER, req.getId()).isEmpty());
    }

    @Test
    void myReview_returnsOwnReview_evenAfterClose() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.submit(REVIEWER, req.getId(), "<p>Feedback.</p>", false);
        PUBLISH.close(AUTHOR, req.getId());
        assertTrue(SERVICE.myReview(REVIEWER, req.getId()).isPresent(),
                "a reviewer keeps read access to their own review after the request closes");
    }

    // =========================================================================
    // Received
    // =========================================================================

    @Test
    void received_andUnreadBadge_andMarkRead() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.submit(REVIEWER, req.getId(), "<p>Clear and well paced.</p>", false);

        List<ReviewReceived> received = SERVICE.received(AUTHOR);
        assertEquals(1, received.size());
        assertEquals("Reviewer_Two", received.get(0).getReviewerHandle());
        assertFalse(received.get(0).isRead());
        assertEquals(1, SERVICE.unreadReceivedCount(AUTHOR));

        SERVICE.markReceivedRead(AUTHOR, received.get(0).getReviewId());
        assertEquals(0, SERVICE.unreadReceivedCount(AUTHOR));
    }

    @Test
    void markReceivedRead_ofAnotherAuthorsReview_is404() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.submit(REVIEWER, req.getId(), "<p>Feedback.</p>", false);
        UUID reviewId = SERVICE.received(AUTHOR).get(0).getReviewId();

        // THIRD does not own the request behind this review.
        ReviewException e = expect(() -> SERVICE.markReceivedRead(THIRD, reviewId));
        assertEquals(404, e.status());
    }

    @Test
    void withdrawnReview_dropsOutOfReceived() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        SERVICE.submit(REVIEWER, req.getId(), "<p>Feedback.</p>", false);
        assertEquals(1, SERVICE.received(AUTHOR).size());

        SERVICE.withdraw(REVIEWER, req.getId());
        assertEquals(0, SERVICE.received(AUTHOR).size());
    }
}
