package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.ReviewSnapshot;

/**
 * {@code review_snapshot} — the immutable half of a Review Package.
 *
 * <p><b>There is no update method, and that is not an omission.</b> A snapshot is
 * written once at publish and never touched again; submitted reviews stay
 * permanently attached to the exact text their author read (spec §8.3). Anything
 * that wants to change published text creates a new request and a new snapshot.
 * If a future caller needs an update here, the requirement is wrong, not the DAO.
 *
 * <p>{@link #insert} takes an external {@link Connection} so the snapshot commits
 * in the same transaction as its request. {@code content_html} is a whole chapter,
 * so list reads go through {@link #findMetaByRequestIds} and never touch it.
 */
public class ReviewSnapshotDao {

    /** Everything except content_html — the columns a list view needs. */
    private static final String META_COLS =
            "id, request_id, source_scope, source_entity_id, source_title, book_title, "
          + "project_title, word_count, source_updated_at, created_at ";

    private static final String FULL_COLS = META_COLS + ", content_html ";

    private final BasicDataSource ds;

    public ReviewSnapshotDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private ReviewSnapshot map(ResultSet rs, boolean withContent) throws SQLException {
        return ReviewSnapshot.builder()
                .id(rs.getObject("id", UUID.class))
                .requestId(rs.getObject("request_id", UUID.class))
                .sourceScope(rs.getString("source_scope"))
                .sourceEntityId(rs.getObject("source_entity_id", UUID.class))
                .sourceTitle(rs.getString("source_title"))
                .bookTitle(rs.getString("book_title"))
                .projectTitle(rs.getString("project_title"))
                .contentHtml(withContent ? rs.getString("content_html") : null)
                .wordCount(rs.getInt("word_count"))
                .sourceUpdatedAt(instant(rs.getTimestamp("source_updated_at")))
                .createdAt(instant(rs.getTimestamp("created_at")))
                .build();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    // =========================================================================
    // Write (once)
    // =========================================================================

    public ReviewSnapshot insert(Connection c, UUID id, UUID requestId, ReviewSnapshot s, Instant now)
            throws SQLException {

        String sql = "INSERT INTO review_snapshot "
                + "(id, request_id, source_scope, source_entity_id, source_title, book_title, "
                + " project_title, content_html, word_count, source_updated_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, requestId);
            ps.setString(3, s.getSourceScope());
            ps.setObject(4, s.getSourceEntityId());
            ps.setString(5, s.getSourceTitle());
            ps.setString(6, s.getBookTitle());
            ps.setString(7, s.getProjectTitle());
            ps.setString(8, s.getContentHtml());
            ps.setInt(9, s.getWordCount());
            ps.setTimestamp(10, s.getSourceUpdatedAt() == null ? null : Timestamp.from(s.getSourceUpdatedAt()));
            ps.setTimestamp(11, Timestamp.from(now));
            ps.executeUpdate();
        }

        return findByRequestId(c, requestId).orElseThrow(
                () -> new SQLException("review_snapshot row vanished immediately after insert: " + id));
    }

    // =========================================================================
    // Reads
    // =========================================================================

    /** The full snapshot, content included — the reviewer's read. */
    public Optional<ReviewSnapshot> findByRequestId(UUID requestId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return findByRequestId(c, requestId);
        }
    }

    private Optional<ReviewSnapshot> findByRequestId(Connection c, UUID requestId) throws SQLException {
        String sql = "SELECT " + FULL_COLS + "FROM review_snapshot WHERE request_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs, true)) : Optional.empty();
            }
        }
    }

    /**
     * Snapshot metadata for a page of requests, keyed by request id. Content is not
     * loaded: a dozen My Requests rows would otherwise drag a dozen whole chapters
     * across the wire to render some titles and word counts.
     */
    public Map<UUID, ReviewSnapshot> findMetaByRequestIds(List<UUID> requestIds) throws SQLException {
        if (requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = requestIds.stream().map(r -> "?").collect(Collectors.joining(","));
        String sql = "SELECT " + META_COLS + "FROM review_snapshot WHERE request_id IN (" + placeholders + ")";

        Map<UUID, ReviewSnapshot> out = new HashMap<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < requestIds.size(); i++) {
                ps.setObject(i + 1, requestIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReviewSnapshot s = map(rs, false);
                    out.put(s.getRequestId(), s);
                }
            }
        }
        return out;
    }
}
