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

import com.richardsand.novelkms.model.review.ContentReport;

/**
 * {@code content_report} — moderation reports filed by users, read and resolved by
 * admins (slice 1F).
 *
 * <p>A report is filed against a request, review, or profile; {@code target_id} is a
 * bare UUID with no foreign key, so the report survives removal of the thing it
 * reported — exactly what a dispute needs. Reads here do NOT filter by who is asking:
 * a report is admin-only data, and every caller of this DAO is behind
 * {@code @RolesAllowed(ADMIN)} except {@link #create}, which is the user-facing file
 * path.
 *
 * <p>All SQL is plain-standard and runs identically on default-mode H2 and
 * PostgreSQL.
 */
public class ContentReportDao {

    public static final String TARGET_REQUEST = "REQUEST";
    public static final String TARGET_REVIEW  = "REVIEW";
    public static final String TARGET_PROFILE = "PROFILE";

    public static final String STATUS_OPEN      = "OPEN";
    public static final String STATUS_RESOLVED  = "RESOLVED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT     = 500;

    private static final String COLS =
            "id, reporter_user_id, target_type, target_id, reason, detail, status, "
          + "resolved_by_user_id, resolution_note, created_at, resolved_at ";

    private final BasicDataSource ds;

    public ContentReportDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private ContentReport map(ResultSet rs) throws SQLException {
        return ContentReport.builder()
                .id(rs.getObject("id", UUID.class))
                .reporterUserId(rs.getObject("reporter_user_id", UUID.class))
                .targetType(rs.getString("target_type"))
                .targetId(rs.getObject("target_id", UUID.class))
                .reason(rs.getString("reason"))
                .detail(rs.getString("detail"))
                .status(rs.getString("status"))
                .resolvedByUserId(rs.getObject("resolved_by_user_id", UUID.class))
                .resolutionNote(rs.getString("resolution_note"))
                .createdAt(instant(rs.getTimestamp("created_at")))
                .resolvedAt(instant(rs.getTimestamp("resolved_at")))
                .build();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    // =========================================================================
    // Create (user-facing)
    // =========================================================================

    /**
     * Files a new OPEN report. The reporter is the caller; the target is validated
     * and resolved to a concrete UUID by the resource before this is called.
     */
    public ContentReport create(UUID reporterUserId, String targetType, UUID targetId,
            String reason, String detail) throws SQLException {

        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO content_report "
                + "(id, reporter_user_id, target_type, target_id, reason, detail, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, reporterUserId);
            ps.setString(3, targetType);
            ps.setObject(4, targetId);
            ps.setString(5, reason);
            ps.setString(6, detail);
            ps.setString(7, STATUS_OPEN);
            ps.executeUpdate();
        }

        return findById(id).orElseThrow(
                () -> new SQLException("content_report row vanished immediately after insert: " + id));
    }

    // =========================================================================
    // Reads (admin)
    // =========================================================================

    public Optional<ContentReport> findById(UUID id) throws SQLException {
        String sql = "SELECT " + COLS + "FROM content_report WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Reports for the admin queue, newest first. A null/blank {@code status} returns
     * every report; otherwise it filters to that status (usually {@code OPEN}).
     */
    public List<ContentReport> listByStatus(String status, Integer limit) throws SQLException {
        boolean all = status == null || status.isBlank();
        String sql = "SELECT " + COLS + "FROM content_report "
                + (all ? "" : "WHERE status = ? ")
                + "ORDER BY created_at DESC, id DESC LIMIT ?";

        List<ContentReport> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (!all) {
                ps.setString(i++, status);
            }
            ps.setInt(i, normalizeLimit(limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    // =========================================================================
    // Resolution (admin)
    // =========================================================================

    /**
     * Marks one report RESOLVED or DISMISSED and stamps who/when/why. Returns the
     * updated row, or empty if no such report exists.
     */
    public Optional<ContentReport> resolve(UUID id, UUID resolverUserId, String status, String note)
            throws SQLException {

        String sql = "UPDATE content_report SET status = ?, resolved_by_user_id = ?, "
                + "resolution_note = ?, resolved_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, resolverUserId);
            ps.setString(3, note);
            ps.setObject(4, id);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    /**
     * Auto-resolves every still-OPEN report against a given target — used when an
     * admin removes the reported request/review or suspends the reported profile, so
     * the moderation action closes out the reports that prompted it rather than
     * leaving orphan OPEN rows behind. Already-resolved reports are left untouched.
     *
     * @return how many OPEN reports were closed
     */
    public int resolveOpenForTarget(String targetType, UUID targetId, UUID resolverUserId, String note)
            throws SQLException {

        String sql = "UPDATE content_report SET status = ?, resolved_by_user_id = ?, "
                + "resolution_note = ?, resolved_at = CURRENT_TIMESTAMP "
                + "WHERE target_type = ? AND target_id = ? AND status = ?";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, STATUS_RESOLVED);
            ps.setObject(2, resolverUserId);
            ps.setString(3, note);
            ps.setString(4, targetType);
            ps.setObject(5, targetId);
            ps.setString(6, STATUS_OPEN);
            return ps.executeUpdate();
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
