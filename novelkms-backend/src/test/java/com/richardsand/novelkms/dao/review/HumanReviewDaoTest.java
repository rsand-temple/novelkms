package com.richardsand.novelkms.dao.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewReceived;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewSnapshot;
import com.richardsand.novelkms.model.review.ReviewWritingSummary;
import com.richardsand.novelkms.service.ReviewPublishService;

/**
 * The storage rules for {@code human_review}: the small DRAFT/SUBMITTED/WITHDRAWN
 * machine, the two list reads, and the two counts that badge and cap the network.
 *
 * <p>The behaviors worth pinning here are the ones a service cannot re-derive: that
 * saving always lands in DRAFT and clears both terminal timestamps (so
 * withdraw-and-rewrite is free), that the writing/received lists carry handles and
 * exclude the states they should, that a block hides a counterparty in both
 * directions, and that the author read-marker refuses a review against someone
 * else's request.
 */
class HumanReviewDaoTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final HumanReviewDao    REVIEWS   = new HumanReviewDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final UUID AUTHOR   = TEST_USER_ID;
    private static final UUID REVIEWER = OTHER_USER_ID;
    private static final UUID THIRD    = THIRD_USER_ID;

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(AUTHOR,   ReviewProfile.builder().handle("Author_One").displayName("Ann Author").build());
        PROFILES.create(REVIEWER, ReviewProfile.builder().handle("Reviewer_Two").displayName("Rev Two").build());
        PROFILES.create(THIRD,    ReviewProfile.builder().handle("Third_Three").build());

        Project project = createTestProject("The Long Road", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private ReviewRequest publish(String title, String genre, String scene) throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, title, null, null);
        UUID sid = sceneDao.create(ch.getId(), "Scene", null).getId();
        sceneDao.saveContent(sid, scene, 0);
        return PUBLISH.publishChapter(AUTHOR, ch.getId(), ReviewRequest.builder().title(title).genre(genre).build());
    }

    private ReviewRequest publish(String title) throws SQLException {
        return publish(title, "fantasy", "<p>One two three four five words here.</p>");
    }

    private UUID snapshotId(UUID requestId) throws SQLException {
        return SNAPSHOTS.findByRequestId(requestId).orElseThrow().getId();
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
    // Insert / find / save
    // =========================================================================

    @Test
    void insert_thenFindByRequestAndReviewer_returnsDraft() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview created = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>Nice work.</p>", 2, HumanReviewDao.VISIBILITY_PRIVATE, false);

        assertEquals(HumanReviewDao.STATUS_DRAFT, created.getStatus());
        assertEquals(2, created.getWordCount());

        HumanReview found = REVIEWS.findByRequestAndReviewer(req.getId(), REVIEWER).orElseThrow();
        assertEquals(created.getId(), found.getId());
        assertEquals("<p>Nice work.</p>", found.getContentHtml());
        assertTrue(REVIEWS.findByRequestAndReviewer(req.getId(), THIRD).isEmpty());
    }

    @Test
    void saveContent_isScopedToReviewer_andReturnsToDraft() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview created = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>Draft.</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.submit(created.getId(), REVIEWER, Instant.now());

        // A different user cannot touch this review.
        assertTrue(REVIEWS.saveContent(created.getId(), THIRD, "<p>hijack</p>", 1,
                HumanReviewDao.VISIBILITY_PRIVATE, false).isEmpty());

        // The owner editing a SUBMITTED review returns it to DRAFT and clears submitted_at.
        HumanReview revised = REVIEWS.saveContent(created.getId(), REVIEWER, "<p>Revised words here.</p>", 3,
                HumanReviewDao.VISIBILITY_PRIVATE, true).orElseThrow();
        assertEquals(HumanReviewDao.STATUS_DRAFT, revised.getStatus());
        assertNull(revised.getSubmittedAt());
        assertTrue(revised.isAiAssisted());
        assertEquals(3, revised.getWordCount());
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    void submit_thenWithdraw_stampsTimestamps() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview created = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>Feedback.</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);

        HumanReview submitted = REVIEWS.submit(created.getId(), REVIEWER, Instant.now()).orElseThrow();
        assertEquals(HumanReviewDao.STATUS_SUBMITTED, submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());

        HumanReview withdrawn = REVIEWS.withdraw(created.getId(), REVIEWER, Instant.now()).orElseThrow();
        assertEquals(HumanReviewDao.STATUS_WITHDRAWN, withdrawn.getStatus());
        assertNotNull(withdrawn.getWithdrawnAt());
    }

    // =========================================================================
    // Counts
    // =========================================================================

    @Test
    void countSubmitted_countsOnlySubmitted() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview r1 = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>x</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        HumanReview r2 = REVIEWS.insert(req.getId(), snapshotId(req.getId()), THIRD,
                "<p>y</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);

        assertEquals(0, REVIEWS.countSubmitted(req.getId()));
        REVIEWS.submit(r1.getId(), REVIEWER, Instant.now());
        assertEquals(1, REVIEWS.countSubmitted(req.getId()));
        REVIEWS.submit(r2.getId(), THIRD, Instant.now());
        assertEquals(2, REVIEWS.countSubmitted(req.getId()));
    }

    @Test
    void countUnreadForAuthor_countsUnreadSubmittedOnly() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview r = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>x</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);

        // A draft does not count.
        assertEquals(0, REVIEWS.countUnreadForAuthor(AUTHOR));
        REVIEWS.submit(r.getId(), REVIEWER, Instant.now());
        assertEquals(1, REVIEWS.countUnreadForAuthor(AUTHOR));

        // Reading it clears it.
        assertTrue(REVIEWS.markAuthorRead(r.getId(), AUTHOR));
        assertEquals(0, REVIEWS.countUnreadForAuthor(AUTHOR));

        // A second mark-read is a no-op.
        assertFalse(REVIEWS.markAuthorRead(r.getId(), AUTHOR));
    }

    @Test
    void markAuthorRead_refusesReviewOfAnotherAuthorsRequest() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        HumanReview r = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>x</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.submit(r.getId(), REVIEWER, Instant.now());

        // THIRD does not own this request, so the guarded UPDATE touches nothing.
        assertFalse(REVIEWS.markAuthorRead(r.getId(), THIRD));
        assertEquals(1, REVIEWS.countUnreadForAuthor(AUTHOR));
    }

    // =========================================================================
    // List reads
    // =========================================================================

    @Test
    void findWritingByReviewer_showsDraftAndSubmitted_notWithdrawn() throws SQLException {
        ReviewRequest a = publish("Chapter A");
        ReviewRequest b = publish("Chapter B");
        ReviewRequest c = publish("Chapter C");

        REVIEWS.insert(a.getId(), snapshotId(a.getId()), REVIEWER, "<p>draft</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        HumanReview sub = REVIEWS.insert(b.getId(), snapshotId(b.getId()), REVIEWER, "<p>done</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.submit(sub.getId(), REVIEWER, Instant.now());
        HumanReview wd = REVIEWS.insert(c.getId(), snapshotId(c.getId()), REVIEWER, "<p>gone</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.withdraw(wd.getId(), REVIEWER, Instant.now());

        List<ReviewWritingSummary> writing = REVIEWS.findWritingByReviewer(REVIEWER);
        assertEquals(2, writing.size());
        assertTrue(writing.stream().allMatch(w -> "Author_One".equals(w.getAuthorHandle())));
        assertTrue(writing.stream().noneMatch(w -> c.getId().equals(w.getRequestId())));
    }

    @Test
    void findReceivedByAuthor_showsSubmittedWithReviewerHandle() throws SQLException {
        ReviewRequest a = publish("Chapter A");
        HumanReview draft = REVIEWS.insert(a.getId(), snapshotId(a.getId()), THIRD, "<p>wip</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        HumanReview sub = REVIEWS.insert(a.getId(), snapshotId(a.getId()), REVIEWER, "<p>Great chapter, clear pacing.</p>", 4, HumanReviewDao.VISIBILITY_PRIVATE, true);
        REVIEWS.submit(sub.getId(), REVIEWER, Instant.now());

        List<ReviewReceived> received = REVIEWS.findReceivedByAuthor(AUTHOR);
        assertEquals(1, received.size(), "only the submitted review is received; the draft is private");
        ReviewReceived row = received.get(0);
        assertEquals("Reviewer_Two", row.getReviewerHandle());
        assertEquals("<p>Great chapter, clear pacing.</p>", row.getContentHtml());
        assertTrue(row.isAiAssisted());
        assertFalse(row.isRead());

        assertNotNull(draft); // silence unused; the draft exists but must not appear above
    }

    @Test
    void block_hidesReceivedAndWriting_bothDirections() throws SQLException {
        ReviewRequest a = publish("Chapter A");
        HumanReview sub = REVIEWS.insert(a.getId(), snapshotId(a.getId()), REVIEWER, "<p>Feedback here.</p>", 2, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.submit(sub.getId(), REVIEWER, Instant.now());

        assertEquals(1, REVIEWS.findReceivedByAuthor(AUTHOR).size());
        assertEquals(1, REVIEWS.findWritingByReviewer(REVIEWER).size());

        // Either direction of block hides the counterparty on both lists and the badge.
        insertBlock(REVIEWER, AUTHOR);
        assertEquals(0, REVIEWS.findReceivedByAuthor(AUTHOR).size());
        assertEquals(0, REVIEWS.findWritingByReviewer(REVIEWER).size());
        assertEquals(0, REVIEWS.countUnreadForAuthor(AUTHOR));
    }
}
