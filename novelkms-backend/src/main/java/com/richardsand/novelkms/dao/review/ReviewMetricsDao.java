package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * Read-time contribution aggregates for the human-review network (spec §13).
 *
 * <p>Contribution is <b>derived, never counted into columns</b> (see the V38
 * migration header): the figures are computed on demand from SUBMITTED reviews,
 * so a review that is withdrawn, redrafted, or removed drops out of them with no
 * bookkeeping. Everything this DAO reads already exists —
 * {@code review_snapshot.word_count}, {@code human_review.word_count}/{@code status},
 * and {@code review_request.author_user_id} — so slice 1E ships without a
 * migration.
 *
 * <p><b>Self-deduping.</b> {@code human_review} carries
 * {@code UNIQUE (request_id, reviewer_user_id)}, so a reviewer holds at most one
 * row per request and each snapshot is summed at most once per reviewer. The
 * status filter is the whole guard against double counting; no DISTINCT is
 * needed.
 *
 * <p><b>No block filtering, deliberately.</b> Unlike {@code HumanReviewDao}'s
 * writing/received <em>lists</em>, these aggregates carry no {@code user_block}
 * predicate. Spec §6.5 wants public metrics objective, so the received count is
 * the owner's true total and reads identically for every viewer; whether a given
 * viewer may <em>see</em> the profile at all is a disclosure decision made above
 * this DAO. Introducing a block predicate here would make the number
 * viewer-relative and is exactly the mistake §6.5 warns against.
 *
 * <p>All SQL is plain-standard (SUM, COUNT, COALESCE, single-table joins), so it
 * runs identically on default-mode H2 and on PostgreSQL.
 */
public class ReviewMetricsDao {

    private final BasicDataSource ds;

    public ReviewMetricsDao(BasicDataSource ds) {
        this.ds = ds;
    }

    /**
     * The four derived figures for one user, in two small reads. The reviewer-side
     * aggregate (words reviewed, review words written, reviews completed) and the
     * received count start from different base tables — {@code human_review} for
     * the former, {@code review_request} for the latter — so folding them into one
     * statement would need a cross join for no benefit; two focused SELECTs are
     * clearer and each hits an existing index.
     *
     * <p>{@code memberSince} is not read here: the caller already holds the
     * {@link com.richardsand.novelkms.model.review.ReviewProfile} whose
     * {@code createdAt} is that value, so re-reading {@code review_profile} would
     * be redundant.
     */
    public Contribution contributionFor(UUID userId) throws SQLException {
        String reviewerSql =
                "SELECT COALESCE(SUM(s.word_count), 0)  AS words_reviewed, "
              + "       COALESCE(SUM(hr.word_count), 0) AS review_words_written, "
              + "       COUNT(*)                        AS reviews_completed "
              + "FROM human_review hr "
              + "JOIN review_snapshot s ON s.id = hr.snapshot_id "
              + "WHERE hr.reviewer_user_id = ? AND hr.status = ?";

        String receivedSql =
                "SELECT COUNT(*) "
              + "FROM human_review hr "
              + "JOIN review_request rr ON rr.id = hr.request_id "
              + "WHERE rr.author_user_id = ? AND hr.status = ?";

        try (Connection c = ds.getConnection()) {
            long wordsReviewed;
            long reviewWordsWritten;
            int  reviewsCompleted;

            try (PreparedStatement ps = c.prepareStatement(reviewerSql)) {
                ps.setObject(1, userId);
                ps.setString(2, HumanReviewDao.STATUS_SUBMITTED);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    wordsReviewed      = rs.getLong("words_reviewed");
                    reviewWordsWritten = rs.getLong("review_words_written");
                    reviewsCompleted   = rs.getInt("reviews_completed");
                }
            }

            int reviewsReceived;
            try (PreparedStatement ps = c.prepareStatement(receivedSql)) {
                ps.setObject(1, userId);
                ps.setString(2, HumanReviewDao.STATUS_SUBMITTED);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    reviewsReceived = rs.getInt(1);
                }
            }

            return new Contribution(wordsReviewed, reviewWordsWritten, reviewsCompleted, reviewsReceived);
        }
    }

    /**
     * The raw derived figures, minus the profile-sourced {@code handle} and
     * {@code memberSince}. The resource assembles these with the profile it already
     * holds into the wire-facing
     * {@link com.richardsand.novelkms.model.review.ProfileMetrics}.
     */
    public record Contribution(long wordsReviewed, long reviewWordsWritten,
            int reviewsCompleted, int reviewsReceived) {
    }
}
