package com.richardsand.novelkms.dao.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.ReviewPublishService;

/**
 * The derived contribution figures (spec §13). These are the numbers a profile
 * shows about a user's participation, so the properties worth pinning are the
 * ones the aggregate must guarantee regardless of how the review machine churns:
 *
 * <ul>
 *   <li>only SUBMITTED reviews count — a draft contributes nothing, and
 *       withdrawing a submitted review removes it again (self-deduping);</li>
 *   <li>words-reviewed sums the <em>snapshot</em> word count while review-words
 *       sums the <em>review</em> body, each once per submitted review;</li>
 *   <li>reviews-received counts submitted reviews against the owner's requests;
 *       and, the load-bearing rule for §6.5, that count is <b>not</b>
 *       block-filtered — the figure is objective, identical for every viewer,
 *       even though the Reviews Received <em>list</em> hides a blocked reviewer.</li>
 * </ul>
 */
class ReviewMetricsDaoTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final HumanReviewDao    REVIEWS   = new HumanReviewDao(ds);
    private static final ReviewMetricsDao  METRICS   = new ReviewMetricsDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final UUID AUTHOR   = TEST_USER_ID;
    private static final UUID REVIEWER = OTHER_USER_ID;
    private static final UUID THIRD    = THIRD_USER_ID;

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(AUTHOR,   ReviewProfile.builder().handle("Author_One").build());
        PROFILES.create(REVIEWER, ReviewProfile.builder().handle("Reviewer_Two").build());
        PROFILES.create(THIRD,    ReviewProfile.builder().handle("Third_Three").build());

        Project project = createTestProject("The Long Road", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Publishes a chapter (owned by AUTHOR) and returns its request. */
    private ReviewRequest publish(String title) throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, title, null, null);
        UUID sid = sceneDao.create(ch.getId(), "Scene", null).getId();
        sceneDao.saveContent(sid, "<p>One two three four five words here.</p>", 0);
        return PUBLISH.publishChapter(AUTHOR, ch.getId(),
                ReviewRequest.builder().title(title).genre("fantasy").build());
    }

    private UUID snapshotId(UUID requestId) throws SQLException {
        return SNAPSHOTS.findByRequestId(requestId).orElseThrow().getId();
    }

    private int snapshotWordCount(UUID requestId) throws SQLException {
        return SNAPSHOTS.findByRequestId(requestId).orElseThrow().getWordCount();
    }

    /** Inserts a DRAFT review by {@code reviewer} with a known body word count. */
    private UUID draft(ReviewRequest req, UUID reviewer, int reviewWords) throws SQLException {
        return REVIEWS.insert(req.getId(), snapshotId(req.getId()), reviewer,
                "<p>feedback</p>", reviewWords, HumanReviewDao.VISIBILITY_PRIVATE, false).getId();
    }

    private void submit(UUID reviewId, UUID reviewer) throws SQLException {
        REVIEWS.submit(reviewId, reviewer, Instant.now());
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
    // Zero / draft state
    // =========================================================================

    @Test
    void user_withNoReviews_isAllZero() throws SQLException {
        ReviewMetricsDao.Contribution c = METRICS.contributionFor(THIRD);
        assertEquals(0L, c.wordsReviewed());
        assertEquals(0L, c.reviewWordsWritten());
        assertEquals(0, c.reviewsCompleted());
        assertEquals(0, c.reviewsReceived());
    }

    @Test
    void draftReview_doesNotCount_untilSubmitted() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        UUID reviewId = draft(req, REVIEWER, 12);

        ReviewMetricsDao.Contribution before = METRICS.contributionFor(REVIEWER);
        assertEquals(0, before.reviewsCompleted());
        assertEquals(0L, before.wordsReviewed());
        assertEquals(0L, before.reviewWordsWritten());

        submit(reviewId, REVIEWER);

        ReviewMetricsDao.Contribution after = METRICS.contributionFor(REVIEWER);
        assertEquals(1, after.reviewsCompleted());
        assertEquals(snapshotWordCount(req.getId()), after.wordsReviewed());
        assertEquals(12L, after.reviewWordsWritten());
    }

    // =========================================================================
    // Reviewer-side sums
    // =========================================================================

    @Test
    void reviewerSums_addAcrossSubmittedReviews() throws SQLException {
        ReviewRequest a = publish("Chapter A");
        ReviewRequest b = publish("Chapter B");

        submit(draft(a, REVIEWER, 30), REVIEWER);
        submit(draft(b, REVIEWER, 45), REVIEWER);

        ReviewMetricsDao.Contribution c = METRICS.contributionFor(REVIEWER);
        assertEquals(2, c.reviewsCompleted());
        assertEquals((long) snapshotWordCount(a.getId()) + snapshotWordCount(b.getId()), c.wordsReviewed());
        assertEquals(75L, c.reviewWordsWritten());
    }

    @Test
    void withdrawingASubmittedReview_removesItFromEveryFigure() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        UUID reviewId = draft(req, REVIEWER, 20);
        submit(reviewId, REVIEWER);

        assertEquals(1, METRICS.contributionFor(REVIEWER).reviewsCompleted());

        REVIEWS.withdraw(reviewId, REVIEWER, Instant.now());

        ReviewMetricsDao.Contribution c = METRICS.contributionFor(REVIEWER);
        assertEquals(0, c.reviewsCompleted());
        assertEquals(0L, c.wordsReviewed());
        assertEquals(0L, c.reviewWordsWritten());
    }

    // =========================================================================
    // Reviews received
    // =========================================================================

    @Test
    void reviewsReceived_countsSubmittedReviewsAgainstOwnRequests() throws SQLException {
        ReviewRequest a = publish("Chapter A");
        ReviewRequest b = publish("Chapter B");

        submit(draft(a, REVIEWER, 10), REVIEWER);
        submit(draft(a, THIRD, 10), THIRD);   // two reviewers on the same request
        submit(draft(b, REVIEWER, 10), REVIEWER);
        draft(b, THIRD, 10);                   // a draft against B — must not count

        assertEquals(3, METRICS.contributionFor(AUTHOR).reviewsReceived());
        // The author reviewed nothing themselves.
        assertEquals(0, METRICS.contributionFor(AUTHOR).reviewsCompleted());
    }

    @Test
    void reviewsReceived_isNotBlockFiltered_soTheFigureIsObjective() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        submit(draft(req, REVIEWER, 10), REVIEWER);

        // A block hides the counterparty from the Reviews Received LIST, but the
        // contribution figure is objective (§6.5): it still counts the review.
        insertBlock(AUTHOR, REVIEWER);

        assertEquals(1, METRICS.contributionFor(AUTHOR).reviewsReceived());
    }
}
