package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.ReviewRequest;

/**
 * CRUD and lifecycle for {@code review_request} — the mutable half of a Review
 * Package.
 *
 * <p>{@link #insert(Connection, UUID, UUID, ReviewRequest, Instant)} takes an
 * external {@link Connection} because a request and its snapshot must be written
 * in one transaction: a request with no snapshot is a package with no manuscript,
 * and there is no sane way to display or recover from one.
 *
 * <p>Every mutating method is scoped by {@code author_user_id} in its own WHERE
 * clause. The tenant filter cannot help here — its segment switch falls through
 * to {@code default -> true} for {@code /review/...} paths — so the scoping has
 * to be in the SQL itself, not in a caller's check that someone might later
 * forget.
 *
 * <p>Reads do NOT filter on status. A WITHDRAWN or CLOSED request is still the
 * author's, and hiding it from them would lose their own history; what to show to
 * whom is a disclosure decision for the layer above.
 */
public class ReviewRequestDao {

    public static final String SCOPE_CHAPTER = "CHAPTER";

    public static final String STATUS_DRAFT     = "DRAFT";
    public static final String STATUS_OPEN      = "OPEN";
    public static final String STATUS_PAUSED    = "PAUSED";
    public static final String STATUS_CLOSED    = "CLOSED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_REMOVED   = "REMOVED";

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_INVITE = "INVITE";

    private static final String COLS =
            "id, author_user_id, source_scope, source_entity_id, title, description, "
          + "author_questions, genre, feedback_types, content_warnings, visibility, status, "
          + "max_reviews, published_at, closes_at, closed_at, created_at, updated_at ";

    private final BasicDataSource ds;

    public ReviewRequestDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private ReviewRequest map(ResultSet rs) throws SQLException {
        return ReviewRequest.builder()
                .id(rs.getObject("id", UUID.class))
                .authorUserId(rs.getObject("author_user_id", UUID.class))
                .sourceScope(rs.getString("source_scope"))
                .sourceEntityId(rs.getObject("source_entity_id", UUID.class))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .authorQuestions(rs.getString("author_questions"))
                .genre(rs.getString("genre"))
                .feedbackTypes(unpack(rs.getString("feedback_types")))
                .contentWarnings(rs.getString("content_warnings"))
                .visibility(rs.getString("visibility"))
                .status(rs.getString("status"))
                .maxReviews(rs.getObject("max_reviews", Integer.class))
                .publishedAt(instant(rs.getTimestamp("published_at")))
                .closesAt(instant(rs.getTimestamp("closes_at")))
                .closedAt(instant(rs.getTimestamp("closed_at")))
                .createdAt(instant(rs.getTimestamp("created_at")))
                .updatedAt(instant(rs.getTimestamp("updated_at")))
                .build();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp timestamp(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    /** Same comma-packing convention as review_profile genres. */
    static String pack(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    static List<String> unpack(String packed) {
        if (packed == null || packed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(packed.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Inserts a request on the caller's connection so it can be committed
     * atomically with its snapshot. Publishes straight to OPEN — Phase 1B has no
     * save-for-later flow, so DRAFT is reachable in the schema but never written.
     */
    public ReviewRequest insert(Connection c, UUID id, UUID authorUserId, ReviewRequest r, Instant now)
            throws SQLException {

        String sql = "INSERT INTO review_request "
                + "(id, author_user_id, source_scope, source_entity_id, title, description, "
                + " author_questions, genre, feedback_types, content_warnings, visibility, status, "
                + " max_reviews, published_at, closes_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, authorUserId);
            ps.setString(3, r.getSourceScope());
            ps.setObject(4, r.getSourceEntityId());
            ps.setString(5, r.getTitle());
            ps.setString(6, r.getDescription());
            ps.setString(7, r.getAuthorQuestions());
            ps.setString(8, r.getGenre());
            ps.setString(9, pack(r.getFeedbackTypes()));
            ps.setString(10, r.getContentWarnings());
            ps.setString(11, r.getVisibility());
            ps.setString(12, r.getStatus());
            ps.setObject(13, r.getMaxReviews());
            ps.setTimestamp(14, timestamp(now));
            ps.setTimestamp(15, timestamp(r.getClosesAt()));
            ps.setTimestamp(16, timestamp(now));
            ps.setTimestamp(17, timestamp(now));
            ps.executeUpdate();
        }

        return findById(c, id).orElseThrow(
                () -> new SQLException("review_request row vanished immediately after insert: " + id));
    }

    /**
     * Updates the author-editable metadata. Source scope, source entity, status,
     * and the snapshot are all deliberately unreachable from here — status moves
     * only through {@link #setStatus}, and the published text never moves at all.
     */
    public Optional<ReviewRequest> update(UUID id, UUID authorUserId, ReviewRequest r) throws SQLException {
        String sql = "UPDATE review_request SET "
                + "title = ?, description = ?, author_questions = ?, genre = ?, "
                + "feedback_types = ?, content_warnings = ?, visibility = ?, "
                + "max_reviews = ?, closes_at = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND author_user_id = ?";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getTitle());
            ps.setString(2, r.getDescription());
            ps.setString(3, r.getAuthorQuestions());
            ps.setString(4, r.getGenre());
            ps.setString(5, pack(r.getFeedbackTypes()));
            ps.setString(6, r.getContentWarnings());
            ps.setString(7, r.getVisibility());
            ps.setObject(8, r.getMaxReviews());
            ps.setTimestamp(9, timestamp(r.getClosesAt()));
            ps.setObject(10, id);
            ps.setObject(11, authorUserId);

            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    /**
     * Moves the lifecycle. {@code closedAt} is stamped for CLOSED and WITHDRAWN
     * and left null otherwise; the caller decides which, because "when did this
     * leave the queue" is a lifecycle question, not a storage one.
     */
    public Optional<ReviewRequest> setStatus(UUID id, UUID authorUserId, String status, Instant closedAt)
            throws SQLException {

        String sql = "UPDATE review_request SET status = ?, closed_at = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND author_user_id = ?";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setTimestamp(2, timestamp(closedAt));
            ps.setObject(3, id);
            ps.setObject(4, authorUserId);

            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    // =========================================================================
    // Reads
    // =========================================================================

    public Optional<ReviewRequest> findById(UUID id) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return findById(c, id);
        }
    }

    private Optional<ReviewRequest> findById(Connection c, UUID id) throws SQLException {
        String sql = "SELECT " + COLS + "FROM review_request WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Newest first — an author's most recent publication is the one they came to look at. */
    public List<ReviewRequest> findByAuthor(UUID authorUserId) throws SQLException {
        String sql = "SELECT " + COLS + "FROM review_request WHERE author_user_id = ? "
                + "ORDER BY created_at DESC";

        List<ReviewRequest> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }
}
