package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.ReviewQueueEntry;

/**
 * The reviewer-facing read of {@code review_request}: the public queue.
 *
 * <p>Kept separate from {@link ReviewRequestDao} (author CRUD) on purpose. The
 * queue is a different concern with a different shape — it joins the request to its
 * snapshot for the word count, to the author's profile for the handle, and to a
 * submitted-review count for the cap, then filters and paginates. None of that
 * belongs in the author's own-requests DAO.
 *
 * <p><b>Every exclusion the queue needs is in the SQL, not layered on afterwards.</b>
 * A request reaches a reviewer only when it is OPEN and PUBLIC, is not the viewer's
 * own, has an ACTIVE (non-suspended) author, is not past its advisory {@code closes_at},
 * has not reached its {@code max_reviews} cap, and involves no block in either
 * direction. Doing this as one query keeps the rules in one place and avoids
 * dragging excluded rows into Java only to drop them.
 *
 * <p>The submitted-review count is a left-joined aggregate and is always zero until
 * slice 1D. Wiring the cap and the count now means 1D adds reviews without revisiting
 * the queue.
 */
public class ReviewQueueDao {

    public static final String SORT_NEWEST = "newest";
    public static final String SORT_OLDEST = "oldest";
    public static final String SORT_FEWEST = "fewest";

    private final BasicDataSource ds;

    public ReviewQueueDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private ReviewQueueEntry map(ResultSet rs) throws SQLException {
        return ReviewQueueEntry.builder()
                .id(rs.getObject("id", UUID.class))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .genre(rs.getString("genre"))
                .feedbackTypes(ReviewRequestDao.unpack(rs.getString("feedback_types")))
                .contentWarnings(rs.getString("content_warnings"))
                .sourceScope(rs.getString("source_scope"))
                .wordCount(rs.getInt("word_count"))
                .publishedAt(rs.getTimestamp("published_at") == null
                        ? null : rs.getTimestamp("published_at").toInstant())
                .authorHandle(rs.getString("author_handle"))
                .authorDisplayName(rs.getString("author_display_name"))
                .reviewCount(rs.getInt("review_count"))
                .maxReviews(rs.getObject("max_reviews", Integer.class))
                .build();
    }

    /**
     * A page of the public queue for one viewer.
     *
     * @param viewerUserId the reviewer; their own requests and any blocked party are excluded
     * @param genre        optional exact (case-insensitive) genre filter
     * @param minWords     optional inclusive lower bound on snapshot word count
     * @param maxWords     optional inclusive upper bound on snapshot word count
     * @param sort         one of {@link #SORT_NEWEST}, {@link #SORT_OLDEST}, {@link #SORT_FEWEST}
     * @param limit        page size (caller clamps to a sane range)
     * @param offset       page offset
     */
    public List<ReviewQueueEntry> findOpenQueue(UUID viewerUserId, String genre,
            Integer minWords, Integer maxWords, String sort, int limit, int offset)
            throws SQLException {

        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder()
                .append("SELECT rr.id, rr.title, rr.description, rr.genre, rr.feedback_types, ")
                .append("       rr.content_warnings, rr.source_scope, rr.published_at, rr.max_reviews, ")
                .append("       p.handle AS author_handle, p.display_name AS author_display_name, ")
                .append("       s.word_count AS word_count, ")
                .append("       COALESCE(rc.cnt, 0) AS review_count ")
                .append("FROM review_request rr ")
                .append("JOIN review_profile p ON p.user_id = rr.author_user_id AND p.status = 'ACTIVE' ")
                .append("JOIN review_snapshot s ON s.request_id = rr.id ")
                .append("LEFT JOIN ( ")
                .append("    SELECT request_id, COUNT(*) AS cnt FROM human_review ")
                .append("    WHERE status = 'SUBMITTED' GROUP BY request_id ")
                .append(") rc ON rc.request_id = rr.id ")
                .append("WHERE rr.status = 'OPEN' ")
                .append("  AND rr.visibility = 'PUBLIC' ")
                .append("  AND rr.author_user_id <> ? ")
                .append("  AND (rr.closes_at IS NULL OR rr.closes_at > CURRENT_TIMESTAMP) ")
                .append("  AND (rr.max_reviews IS NULL OR COALESCE(rc.cnt, 0) < rr.max_reviews) ")
                .append("  AND NOT EXISTS ( ")
                .append("      SELECT 1 FROM user_block b ")
                .append("      WHERE (b.blocker_user_id = ? AND b.blocked_user_id = rr.author_user_id) ")
                .append("         OR (b.blocker_user_id = rr.author_user_id AND b.blocked_user_id = ?) ")
                .append("  ) ");

        // viewer id: one for the own-request exclusion, two for the symmetric block check.
        params.add(viewerUserId);
        params.add(viewerUserId);
        params.add(viewerUserId);

        if (genre != null && !genre.isBlank()) {
            sql.append("  AND LOWER(rr.genre) = LOWER(?) ");
            params.add(genre.trim());
        }
        if (minWords != null) {
            sql.append("  AND s.word_count >= ? ");
            params.add(minWords);
        }
        if (maxWords != null) {
            sql.append("  AND s.word_count <= ? ");
            params.add(maxWords);
        }

        sql.append("ORDER BY ").append(orderBy(sort)).append(" ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<ReviewQueueEntry> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Submitted reviews for one request. Zero until slice 1D; wired now so the
     * package view reports the same count the queue does.
     */
    public int countSubmittedReviews(UUID requestId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM human_review WHERE request_id = ? AND status = 'SUBMITTED'";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * The ORDER BY clause for a normalized sort key. Chronological by default (§12:
     * "a chronological queue with basic filters is more transparent"); FEWEST
     * surfaces the requests that most need a reviewer (§29.1). Never interpolates
     * caller input — {@code sort} is one of three known constants and anything else
     * falls through to newest.
     */
    private static String orderBy(String sort) {
        if (SORT_OLDEST.equals(sort)) {
            return "rr.published_at ASC, rr.created_at ASC";
        }
        if (SORT_FEWEST.equals(sort)) {
            return "COALESCE(rc.cnt, 0) ASC, rr.published_at DESC";
        }
        return "rr.published_at DESC, rr.created_at DESC";
    }
}
