package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewReceived;
import com.richardsand.novelkms.model.review.ReviewWritingSummary;

/**
 * {@code human_review} — a reviewer's review of a package, and the two list reads
 * the review surfaces need.
 *
 * <p><b>Scoping lives in the SQL, not in a caller's memory.</b> As with every
 * review-network table, {@code TenantAuthorizationFilter} falls through to
 * {@code default -> true} for {@code /review/...} paths, so nothing upstream
 * scopes these rows for us. Every write is therefore keyed by the acting user in
 * its own WHERE clause ({@code reviewer_user_id} for the reviewer's own review,
 * a request-ownership subquery for the author's read-marker), and both list reads
 * filter to the caller and drop any counterparty a block hides in either
 * direction.
 *
 * <p><b>The status machine is small on purpose.</b> A review is DRAFT, SUBMITTED,
 * or WITHDRAWN (REMOVED is an admin action, slice 1F). {@link #saveContent} always
 * lands the row in DRAFT and clears both terminal timestamps, which is what makes
 * "withdraw and rewrite" (spec §30.2 Q6) a single rule rather than a special case:
 * a WITHDRAWN or SUBMITTED review edited again simply returns to DRAFT.
 *
 * <p>Reads do not filter on request status here — a reviewer keeps access to their
 * own review after the request closes (§30.2 Q5). What is and is not <em>writable</em>
 * given the request's status is a service decision, not a DAO one.
 *
 * <p>All SQL is plain-standard (LIMIT/OFFSET are not even needed; NOT EXISTS,
 * COALESCE, IN-subquery, CURRENT_TIMESTAMP only), so it runs identically on the
 * default-mode H2 test database and on PostgreSQL.
 */
public class HumanReviewDao {

    public static final String STATUS_DRAFT     = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_REMOVED   = "REMOVED";

    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_PUBLIC  = "PUBLIC";

    private static final String COLS =
            "id, request_id, snapshot_id, reviewer_user_id, status, visibility, content_html, "
          + "word_count, ai_assisted, created_at, updated_at, submitted_at, withdrawn_at, author_read_at ";

    private final BasicDataSource ds;

    public HumanReviewDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private HumanReview map(ResultSet rs) throws SQLException {
        return HumanReview.builder()
                .id(rs.getObject("id", UUID.class))
                .requestId(rs.getObject("request_id", UUID.class))
                .snapshotId(rs.getObject("snapshot_id", UUID.class))
                .reviewerUserId(rs.getObject("reviewer_user_id", UUID.class))
                .status(rs.getString("status"))
                .visibility(rs.getString("visibility"))
                .contentHtml(rs.getString("content_html"))
                .wordCount(rs.getInt("word_count"))
                .aiAssisted(rs.getBoolean("ai_assisted"))
                .createdAt(instant(rs.getTimestamp("created_at")))
                .updatedAt(instant(rs.getTimestamp("updated_at")))
                .submittedAt(instant(rs.getTimestamp("submitted_at")))
                .withdrawnAt(instant(rs.getTimestamp("withdrawn_at")))
                .authorReadAt(instant(rs.getTimestamp("author_read_at")))
                .build();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp timestamp(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    // =========================================================================
    // Reads
    // =========================================================================

    public Optional<HumanReview> findById(UUID id) throws SQLException {
        return findOne("SELECT " + COLS + "FROM human_review WHERE id = ?", id);
    }

    /** The caller's own review of one package, if they have started one. */
    public Optional<HumanReview> findByRequestAndReviewer(UUID requestId, UUID reviewerUserId)
            throws SQLException {

        String sql = "SELECT " + COLS + "FROM human_review WHERE request_id = ? AND reviewer_user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, requestId);
            ps.setObject(2, reviewerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Optional<HumanReview> findOne(String sql, UUID key) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** The author_user_id of the request a given review is against, for read-marker authorization. */
    public Optional<UUID> findRequestAuthor(UUID reviewId) throws SQLException {
        String sql = "SELECT rr.author_user_id "
                + "FROM human_review hr JOIN review_request rr ON rr.id = hr.request_id "
                + "WHERE hr.id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getObject("author_user_id", UUID.class)) : Optional.empty();
            }
        }
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Creates a fresh DRAFT review. The reviewer's first save of a package. The
     * {@code UNIQUE(request_id, reviewer_user_id)} constraint is the real guard
     * against a duplicate; the service's existence check exists only to route an
     * edit to {@link #saveContent} instead.
     */
    public HumanReview insert(UUID requestId, UUID snapshotId, UUID reviewerUserId,
            String contentHtml, int wordCount, String visibility, boolean aiAssisted)
            throws SQLException {

        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO human_review "
                + "(id, request_id, snapshot_id, reviewer_user_id, status, visibility, content_html, "
                + " word_count, ai_assisted, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, requestId);
            ps.setObject(3, snapshotId);
            ps.setObject(4, reviewerUserId);
            ps.setString(5, STATUS_DRAFT);
            ps.setString(6, visibility);
            ps.setString(7, contentHtml);
            ps.setInt(8, wordCount);
            ps.setBoolean(9, aiAssisted);
            ps.executeUpdate();
        }

        return findById(id).orElseThrow(
                () -> new SQLException("human_review row vanished immediately after insert: " + id));
    }

    /**
     * Saves review content and returns the row to DRAFT, clearing both terminal
     * timestamps. This is the one rule behind "withdraw and rewrite" and behind
     * revising a submission: whatever the row was, editing it makes it a draft
     * again. Scoped by {@code reviewer_user_id} so it can only touch the caller's
     * own review.
     */
    public Optional<HumanReview> saveContent(UUID reviewId, UUID reviewerUserId,
            String contentHtml, int wordCount, String visibility, boolean aiAssisted)
            throws SQLException {

        String sql = "UPDATE human_review SET "
                + "content_html = ?, word_count = ?, visibility = ?, ai_assisted = ?, "
                + "status = ?, submitted_at = NULL, withdrawn_at = NULL, "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND reviewer_user_id = ?";

        return applyUpdate(sql, ps -> {
            ps.setString(1, contentHtml);
            ps.setInt(2, wordCount);
            ps.setString(3, visibility);
            ps.setBoolean(4, aiAssisted);
            ps.setString(5, STATUS_DRAFT);
            ps.setObject(6, reviewId);
            ps.setObject(7, reviewerUserId);
        }, reviewId);
    }

    /** DRAFT -> SUBMITTED, stamping {@code submitted_at}. Scoped to the caller. */
    public Optional<HumanReview> submit(UUID reviewId, UUID reviewerUserId, Instant now) throws SQLException {
        String sql = "UPDATE human_review SET status = ?, submitted_at = ?, withdrawn_at = NULL, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND reviewer_user_id = ?";

        return applyUpdate(sql, ps -> {
            ps.setString(1, STATUS_SUBMITTED);
            ps.setTimestamp(2, timestamp(now));
            ps.setObject(3, reviewId);
            ps.setObject(4, reviewerUserId);
        }, reviewId);
    }

    /** DRAFT/SUBMITTED -> WITHDRAWN, stamping {@code withdrawn_at}. Scoped to the caller. */
    public Optional<HumanReview> withdraw(UUID reviewId, UUID reviewerUserId, Instant now) throws SQLException {
        String sql = "UPDATE human_review SET status = ?, withdrawn_at = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND reviewer_user_id = ?";

        return applyUpdate(sql, ps -> {
            ps.setString(1, STATUS_WITHDRAWN);
            ps.setTimestamp(2, timestamp(now));
            ps.setObject(3, reviewId);
            ps.setObject(4, reviewerUserId);
        }, reviewId);
    }

    /**
     * Stamps the author's first-read marker on a review, but only if the review is
     * against a request the given author owns and has not already been read. The
     * ownership subquery is the authorization: a forged review id belonging to
     * someone else's request updates zero rows.
     *
     * @return whether a row was newly marked read (false if not owned or already read)
     */
    public boolean markAuthorRead(UUID reviewId, UUID authorUserId) throws SQLException {
        String sql = "UPDATE human_review SET author_read_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND author_read_at IS NULL "
                + "AND request_id IN (SELECT id FROM review_request WHERE author_user_id = ?)";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, reviewId);
            ps.setObject(2, authorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Moderation status change (slice 1F). Unlike {@link #submit}/{@link #withdraw},
     * this is NOT scoped by {@code reviewer_user_id}: an administrator removes a
     * review they did not write. Reachable only from {@code AdminModerationResource}
     * ({@code @RolesAllowed(ADMIN)}), so the role annotation — not a WHERE clause —
     * is the guard. Setting REMOVED drops the review from every list read for free:
     * the received/metrics reads count SUBMITTED only, and the writing read filters
     * to DRAFT/SUBMITTED. The caller captures the prior status for the audit trail
     * before invoking this.
     */
    public Optional<HumanReview> adminSetStatus(UUID id, String status) throws SQLException {
        String sql = "UPDATE human_review SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return applyUpdate(sql, ps -> {
            ps.setString(1, status);
            ps.setObject(2, id);
        }, id);
    }

    // =========================================================================
    // Counts
    // =========================================================================

    /**
     * Submitted reviews against one request. The author's {@code max_reviews} cap
     * is enforced against this at submit time.
     */
    public int countSubmitted(UUID requestId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM human_review WHERE request_id = ? AND status = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, requestId);
            ps.setString(2, STATUS_SUBMITTED);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Unread submitted reviews across all of an author's requests — the Reviews
     * Received badge. Block-filtered in both directions so a blocked reviewer's
     * feedback neither shows nor counts.
     */
    public int countUnreadForAuthor(UUID authorUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM human_review hr "
                + "JOIN review_request rr ON rr.id = hr.request_id "
                + "WHERE rr.author_user_id = ? AND hr.status = ? AND hr.author_read_at IS NULL "
                + "AND NOT EXISTS ( "
                + "    SELECT 1 FROM user_block b "
                + "    WHERE (b.blocker_user_id = rr.author_user_id AND b.blocked_user_id = hr.reviewer_user_id) "
                + "       OR (b.blocker_user_id = hr.reviewer_user_id AND b.blocked_user_id = rr.author_user_id) "
                + ")";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, authorUserId);
            ps.setString(2, STATUS_SUBMITTED);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // =========================================================================
    // List reads
    // =========================================================================

    /**
     * The reviewer's own active reviews — DRAFT and SUBMITTED — newest activity
     * first. WITHDRAWN and REMOVED are excluded: a retracted review is not work in
     * progress. Joined to the request (title, current status), the snapshot
     * (source titles, length), and the author's profile (handle). Block-filtered
     * both directions.
     */
    public List<ReviewWritingSummary> findWritingByReviewer(UUID reviewerUserId) throws SQLException {
        String sql = "SELECT hr.id AS review_id, rr.id AS request_id, rr.title AS request_title, "
                + "       rr.status AS request_status, "
                + "       ap.handle AS author_handle, ap.display_name AS author_display_name, "
                + "       s.source_title, s.book_title, s.word_count AS snapshot_word_count, "
                + "       hr.status, hr.word_count, hr.ai_assisted, hr.updated_at, hr.submitted_at "
                + "FROM human_review hr "
                + "JOIN review_request  rr ON rr.id = hr.request_id "
                + "JOIN review_snapshot s  ON s.id  = hr.snapshot_id "
                + "JOIN review_profile  ap ON ap.user_id = rr.author_user_id "
                + "WHERE hr.reviewer_user_id = ? "
                + "  AND hr.status IN (?, ?) "
                + "  AND NOT EXISTS ( "
                + "      SELECT 1 FROM user_block b "
                + "      WHERE (b.blocker_user_id = hr.reviewer_user_id AND b.blocked_user_id = rr.author_user_id) "
                + "         OR (b.blocker_user_id = rr.author_user_id AND b.blocked_user_id = hr.reviewer_user_id) "
                + "  ) "
                + "ORDER BY hr.updated_at DESC";

        List<ReviewWritingSummary> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, reviewerUserId);
            ps.setString(2, STATUS_DRAFT);
            ps.setString(3, STATUS_SUBMITTED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(ReviewWritingSummary.builder()
                            .reviewId(rs.getObject("review_id", UUID.class))
                            .requestId(rs.getObject("request_id", UUID.class))
                            .requestTitle(rs.getString("request_title"))
                            .authorHandle(rs.getString("author_handle"))
                            .authorDisplayName(rs.getString("author_display_name"))
                            .sourceTitle(rs.getString("source_title"))
                            .bookTitle(rs.getString("book_title"))
                            .snapshotWordCount(rs.getInt("snapshot_word_count"))
                            .status(rs.getString("status"))
                            .wordCount(rs.getInt("word_count"))
                            .aiAssisted(rs.getBoolean("ai_assisted"))
                            .updatedAt(instant(rs.getTimestamp("updated_at")))
                            .submittedAt(instant(rs.getTimestamp("submitted_at")))
                            .requestStatus(rs.getString("request_status"))
                            .build());
                }
            }
        }
        return out;
    }

    /**
     * Submitted reviews of the author's own requests — the feedback they have
     * received, newest first. DRAFT and WITHDRAWN are excluded: a draft is private
     * to its reviewer and a withdrawn review has been retracted. Carries the review
     * body and the reviewer's handle (never their user id). Block-filtered both
     * directions.
     */
    public List<ReviewReceived> findReceivedByAuthor(UUID authorUserId) throws SQLException {
        String sql = "SELECT hr.id AS review_id, rr.id AS request_id, rr.title AS request_title, "
                + "       rp.handle AS reviewer_handle, rp.display_name AS reviewer_display_name, "
                + "       s.source_title, s.book_title, "
                + "       hr.content_html, hr.word_count, hr.ai_assisted, hr.submitted_at, hr.author_read_at "
                + "FROM human_review hr "
                + "JOIN review_request  rr ON rr.id = hr.request_id "
                + "JOIN review_snapshot s  ON s.id  = hr.snapshot_id "
                + "JOIN review_profile  rp ON rp.user_id = hr.reviewer_user_id "
                + "WHERE rr.author_user_id = ? "
                + "  AND hr.status = ? "
                + "  AND NOT EXISTS ( "
                + "      SELECT 1 FROM user_block b "
                + "      WHERE (b.blocker_user_id = rr.author_user_id AND b.blocked_user_id = hr.reviewer_user_id) "
                + "         OR (b.blocker_user_id = hr.reviewer_user_id AND b.blocked_user_id = rr.author_user_id) "
                + "  ) "
                + "ORDER BY hr.submitted_at DESC";

        List<ReviewReceived> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, authorUserId);
            ps.setString(2, STATUS_SUBMITTED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(ReviewReceived.builder()
                            .reviewId(rs.getObject("review_id", UUID.class))
                            .requestId(rs.getObject("request_id", UUID.class))
                            .requestTitle(rs.getString("request_title"))
                            .reviewerHandle(rs.getString("reviewer_handle"))
                            .reviewerDisplayName(rs.getString("reviewer_display_name"))
                            .sourceTitle(rs.getString("source_title"))
                            .bookTitle(rs.getString("book_title"))
                            .contentHtml(rs.getString("content_html"))
                            .wordCount(rs.getInt("word_count"))
                            .aiAssisted(rs.getBoolean("ai_assisted"))
                            .submittedAt(instant(rs.getTimestamp("submitted_at")))
                            .read(rs.getTimestamp("author_read_at") != null)
                            .build());
                }
            }
        }
        return out;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Applies a scoped UPDATE and re-reads the row, or empty when nothing matched. */
    private Optional<HumanReview> applyUpdate(String sql, Binder binder, UUID reviewId) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(reviewId);
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
