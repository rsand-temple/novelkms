package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewQueueDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewPackage;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewQueueEntry;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewSnapshot;

/**
 * The reviewer's read side — the first cross-user read path in NovelKMS. What this
 * suite pins down is exactly what a stranger may and may not see, because getting it
 * wrong leaks either a manuscript or the fact that one exists:
 *
 * <ul>
 *   <li><b>Only OPEN, PUBLIC packages reach a stranger</b>, and every failure is a
 *       404 — never a 403 that would confirm the package exists.</li>
 *   <li><b>The author always reads their own</b>, in any status.</li>
 *   <li><b>The queue's exclusions are real:</b> own requests, non-open, invite-only,
 *       past-close, capped, blocked (either direction), suspended author.</li>
 *   <li><b>A handle gates participation;</b> suspension revokes it.</li>
 * </ul>
 */
class ReviewAccessServiceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final ReviewQueueDao    QUEUE     = new ReviewQueueDao(ds);
    private static final UserBlockDao      BLOCKS    = new UserBlockDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final ReviewAccessService ACCESS = new ReviewAccessService(
            REQUESTS, SNAPSHOTS, PROFILES, QUEUE, BLOCKS);

    // TEST_USER authors; OTHER_USER reviews. THIRD_USER is a bystander with no profile.
    private static final UUID AUTHOR   = TEST_USER_ID;
    private static final UUID REVIEWER = OTHER_USER_ID;

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();

        PROFILES.create(AUTHOR,   ReviewProfile.builder().handle("Author_One").displayName("Ann Author").build());
        PROFILES.create(REVIEWER, ReviewProfile.builder().handle("Reviewer_Two").build());

        Project project = createTestProject("The Long Road", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Publishes a fresh chapter as the author and returns the resulting OPEN request. */
    private ReviewRequest publish(String title, String genre, String... sceneHtml) throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, title, null, null);
        for (String html : sceneHtml) {
            UUID sid = sceneDao.create(ch.getId(), "Scene", null).getId();
            sceneDao.saveContent(sid, html, 0);
        }
        return PUBLISH.publishChapter(AUTHOR, ch.getId(),
                ReviewRequest.builder().title(title).genre(genre).build());
    }

    private ReviewRequest publish(String title) throws SQLException {
        return publish(title, null, "<p>Some words to review here.</p>");
    }

    private static ReviewException expect(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReviewException.class, e);
    }

    private List<ReviewQueueEntry> reviewerQueue() throws SQLException {
        return ACCESS.queue(REVIEWER, null, null, null, null, null, null);
    }

    // ---- Raw fixtures for the columns/tables 1C reads but does not yet write ----

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

    /** A submitted human review — 1D's job, inserted directly so 1C's cap/count can be tested now. */
    private void insertSubmittedReview(UUID requestId, UUID reviewer) throws SQLException {
        UUID snapshotId = SNAPSHOTS.findByRequestId(requestId).orElseThrow().getId();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO human_review (id, request_id, snapshot_id, reviewer_user_id, status) "
                      + "VALUES (?, ?, ?, ?, 'SUBMITTED')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, requestId);
            ps.setObject(3, snapshotId);
            ps.setObject(4, reviewer);
            ps.executeUpdate();
        }
    }

    private void setClosesAt(UUID requestId, Instant when) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE review_request SET closes_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, when == null ? null : Timestamp.from(when));
            ps.setObject(2, requestId);
            ps.executeUpdate();
        }
    }

    private void setMaxReviews(UUID requestId, Integer cap) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE review_request SET max_reviews = ? WHERE id = ?")) {
            ps.setObject(1, cap);
            ps.setObject(2, requestId);
            ps.executeUpdate();
        }
    }

    private void setVisibilityInvite(UUID requestId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE review_request SET visibility = 'INVITE' WHERE id = ?")) {
            ps.setObject(1, requestId);
            ps.executeUpdate();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // Queue — what appears
    // =========================================================================

    @Test
    void queue_listsAnotherAuthorsOpenPublicRequest() throws SQLException {
        ReviewRequest r = publish("Chapter for review", "fantasy", "<p>One two three four.</p>");

        List<ReviewQueueEntry> queue = reviewerQueue();

        assertEquals(1, queue.size());
        ReviewQueueEntry e = queue.get(0);
        assertEquals(r.getId(), e.getId());
        assertEquals("Author_One", e.getAuthorHandle());
        assertEquals("Ann Author", e.getAuthorDisplayName());
        assertEquals(4, e.getWordCount());
        assertEquals(0, e.getReviewCount());
    }

    @Test
    void queue_excludesTheViewersOwnRequests() throws SQLException {
        publish("Mine");

        // The author browsing the queue does not see their own package.
        assertTrue(ACCESS.queue(AUTHOR, null, null, null, null, null, null).isEmpty());
    }

    @Test
    void queue_excludesPausedClosedAndWithdrawn() throws SQLException {
        ReviewRequest r = publish("Will pause");

        PUBLISH.pause(AUTHOR, r.getId());
        assertTrue(reviewerQueue().isEmpty(), "paused must leave the queue");

        PUBLISH.resume(AUTHOR, r.getId());
        assertEquals(1, reviewerQueue().size(), "resumed returns to the queue");

        PUBLISH.close(AUTHOR, r.getId());
        assertTrue(reviewerQueue().isEmpty(), "closed must leave the queue");
    }

    @Test
    void queue_excludesWithdrawn() throws SQLException {
        ReviewRequest r = publish("Will withdraw");
        PUBLISH.withdraw(AUTHOR, r.getId());

        assertTrue(reviewerQueue().isEmpty());
    }

    @Test
    void queue_excludesInviteOnlyRequests() throws SQLException {
        ReviewRequest r = publish("Invite only");
        setVisibilityInvite(r.getId());

        assertTrue(reviewerQueue().isEmpty());
    }

    @Test
    void queue_excludesRequestsPastTheirClosesAt() throws SQLException {
        ReviewRequest r = publish("Expired");
        setClosesAt(r.getId(), Instant.now().minus(1, ChronoUnit.DAYS));

        assertTrue(reviewerQueue().isEmpty());

        // A future closing date does not exclude it.
        setClosesAt(r.getId(), Instant.now().plus(1, ChronoUnit.DAYS));
        assertEquals(1, reviewerQueue().size());
    }

    @Test
    void queue_excludesRequestsAtTheirReviewCap() throws SQLException {
        ReviewRequest r = publish("Capped");
        setMaxReviews(r.getId(), 1);
        insertSubmittedReview(r.getId(), THIRD_USER_ID);

        assertTrue(reviewerQueue().isEmpty(), "a request at its cap should not be offered");
    }

    @Test
    void queue_belowCap_stillAppears_andCountsTheReview() throws SQLException {
        ReviewRequest r = publish("Room for more");
        setMaxReviews(r.getId(), 3);
        insertSubmittedReview(r.getId(), THIRD_USER_ID);

        List<ReviewQueueEntry> queue = reviewerQueue();
        assertEquals(1, queue.size());
        assertEquals(1, queue.get(0).getReviewCount());
    }

    @Test
    void queue_excludesBlocked_inEitherDirection() throws SQLException {
        ReviewRequest r = publish("Blocked either way");

        insertBlock(REVIEWER, AUTHOR);            // reviewer blocked the author
        assertTrue(reviewerQueue().isEmpty());

        truncateBlocks();
        insertBlock(AUTHOR, REVIEWER);            // author blocked the reviewer
        assertTrue(reviewerQueue().isEmpty());

        assertNotNull(r);
    }

    @Test
    void queue_excludesSuspendedAuthors() throws SQLException {
        publish("From a suspended author");
        PROFILES.setStatus(AUTHOR, ReviewProfileDao.STATUS_SUSPENDED);

        assertTrue(reviewerQueue().isEmpty());
    }

    // =========================================================================
    // Queue — filters, sort, paging
    // =========================================================================

    @Test
    void queue_filtersByGenre_caseInsensitively() throws SQLException {
        publish("A fantasy", "Fantasy", "<p>Dragons here.</p>");
        publish("A horror",  "Horror",  "<p>Ghosts here.</p>");

        List<ReviewQueueEntry> fantasy = ACCESS.queue(REVIEWER, "fantasy", null, null, null, null, null);

        assertEquals(1, fantasy.size());
        assertEquals("A fantasy", fantasy.get(0).getTitle());
    }

    @Test
    void queue_filtersByWordCount() throws SQLException {
        publish("Short", null, "<p>Two words.</p>");                       // 2 words
        publish("Long",  null, "<p>One two three four five six seven.</p>"); // 7 words

        List<ReviewQueueEntry> big = ACCESS.queue(REVIEWER, null, 5, null, null, null, null);

        assertEquals(1, big.size());
        assertEquals("Long", big.get(0).getTitle());
    }

    @Test
    void queue_sortsNewestOrOldestFirst() throws SQLException {
        publish("First");
        sleep();
        publish("Second");

        assertEquals("Second", ACCESS.queue(REVIEWER, null, null, null, "newest", null, null).get(0).getTitle());
        assertEquals("First",  ACCESS.queue(REVIEWER, null, null, null, "oldest", null, null).get(0).getTitle());
    }

    @Test
    void queue_sortFewest_putsLeastReviewedFirst() throws SQLException {
        ReviewRequest a = publish("Has one review");
        sleep();
        publish("Has none");                       // newer, but zero reviews

        setMaxReviews(a.getId(), 5);
        insertSubmittedReview(a.getId(), THIRD_USER_ID);

        List<ReviewQueueEntry> fewest = ACCESS.queue(REVIEWER, null, null, null, "fewest", null, null);
        assertEquals("Has none", fewest.get(0).getTitle(), "zero reviews should sort ahead of one");
    }

    @Test
    void queue_paginates() throws SQLException {
        publish("One");
        sleep();
        publish("Two");
        sleep();
        publish("Three");

        assertEquals(2, ACCESS.queue(REVIEWER, null, null, null, "newest", 2, 0).size());
        assertEquals(1, ACCESS.queue(REVIEWER, null, null, null, "newest", 2, 2).size());
    }

    // =========================================================================
    // Participation gate
    // =========================================================================

    @Test
    void queue_withoutAProfile_requiresOne() throws SQLException {
        publish("Anything");

        // THIRD_USER never claimed a handle.
        ReviewException e = expect(() -> ACCESS.queue(THIRD_USER_ID, null, null, null, null, null, null));

        assertEquals(409, e.status());
        assertEquals("profile_required", e.code());
    }

    @Test
    void queue_suspendedViewer_isForbidden() throws SQLException {
        publish("Anything");
        PROFILES.setStatus(REVIEWER, ReviewProfileDao.STATUS_SUSPENDED);

        ReviewException e = expect(() -> reviewerQueue());

        assertEquals(403, e.status());
        assertEquals("suspended", e.code());
    }

    // =========================================================================
    // Package view
    // =========================================================================

    @Test
    void package_returnsMetadataToAnEligibleReviewer() throws SQLException {
        ReviewRequest r = publish("A package", "literary", "<p>One two three.</p>");

        ReviewPackage pkg = ACCESS.viewPackage(REVIEWER, r.getId());

        assertEquals(r.getId(), pkg.getId());
        assertEquals("Author_One", pkg.getAuthorHandle());
        assertEquals("A package", pkg.getTitle());
        assertEquals("Book One", pkg.getBookTitle());
        assertEquals(3, pkg.getWordCount());
        assertEquals(0, pkg.getReviewCount());
    }

    @Test
    void package_countsSubmittedReviews() throws SQLException {
        ReviewRequest r = publish("Reviewed once");
        insertSubmittedReview(r.getId(), THIRD_USER_ID);

        assertEquals(1, ACCESS.viewPackage(REVIEWER, r.getId()).getReviewCount());
    }

    @Test
    void package_authorCanViewOwn_inAnyStatus() throws SQLException {
        ReviewRequest r = publish("My own");
        PUBLISH.pause(AUTHOR, r.getId());

        // Paused is invisible to strangers but the author still reads it.
        assertNotNull(ACCESS.viewPackage(AUTHOR, r.getId()));
        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, r.getId())).status());
    }

    @Test
    void package_nonOpen_is404ToAStranger() throws SQLException {
        ReviewRequest r = publish("Will close");
        PUBLISH.close(AUTHOR, r.getId());

        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, r.getId())).status(),
                "403 would confirm the package exists");
    }

    @Test
    void package_inviteOnly_is404ToAStranger() throws SQLException {
        ReviewRequest r = publish("Invite only");
        setVisibilityInvite(r.getId());

        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, r.getId())).status());
    }

    @Test
    void package_blocked_is404() throws SQLException {
        ReviewRequest r = publish("Blocked");
        insertBlock(AUTHOR, REVIEWER);

        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, r.getId())).status());
    }

    @Test
    void package_suspendedAuthor_is404ToAStranger() throws SQLException {
        ReviewRequest r = publish("Suspended author");
        PROFILES.setStatus(AUTHOR, ReviewProfileDao.STATUS_SUSPENDED);

        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, r.getId())).status());
    }

    @Test
    void package_unknown_is404() {
        assertEquals(404, expect(() -> ACCESS.viewPackage(REVIEWER, UUID.randomUUID())).status());
    }

    @Test
    void package_withoutAProfile_requiresOne() throws SQLException {
        ReviewRequest r = publish("Anything");

        ReviewException e = expect(() -> ACCESS.viewPackage(THIRD_USER_ID, r.getId()));
        assertEquals(409, e.status());
        assertEquals("profile_required", e.code());
    }

    // =========================================================================
    // Snapshot reader
    // =========================================================================

    @Test
    void snapshot_returnsFrozenContentToAnEligibleReviewer() throws SQLException {
        ReviewRequest r = publish("Readable", null, "<p>Frozen prose.</p>");

        ReviewSnapshot s = ACCESS.snapshot(REVIEWER, r.getId());

        assertEquals("<p>Frozen prose.</p>", s.getContentHtml());
    }

    @Test
    void snapshot_nonOpen_is404ToAStranger() throws SQLException {
        ReviewRequest r = publish("Will pause");
        PUBLISH.pause(AUTHOR, r.getId());

        assertEquals(404, expect(() -> ACCESS.snapshot(REVIEWER, r.getId())).status());
    }

    @Test
    void snapshot_authorReadsOwn_inAnyStatus() throws SQLException {
        ReviewRequest r = publish("My own", null, "<p>Frozen prose.</p>");
        PUBLISH.pause(AUTHOR, r.getId());

        assertEquals("<p>Frozen prose.</p>", ACCESS.snapshot(AUTHOR, r.getId()).getContentHtml());
    }

    @Test
    void snapshot_blocked_is404() throws SQLException {
        ReviewRequest r = publish("Blocked");
        insertBlock(REVIEWER, AUTHOR);

        assertEquals(404, expect(() -> ACCESS.snapshot(REVIEWER, r.getId())).status());
    }

    @Test
    void snapshot_withoutAProfile_requiresOne() throws SQLException {
        ReviewRequest r = publish("Anything");

        assertEquals(409, expect(() -> ACCESS.snapshot(THIRD_USER_ID, r.getId())).status());
    }

    private void truncateBlocks() throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM user_block")) {
            ps.executeUpdate();
        }
    }
}
