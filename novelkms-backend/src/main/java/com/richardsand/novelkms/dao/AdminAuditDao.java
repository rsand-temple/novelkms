package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.AdminAuditLogEntry;

/**
 * Audit log for admin/support actions.
 *
 * This DAO should be called by admin services/resources before or immediately
 * after any admin mutation such as granting family access, extending a trial,
 * disabling a user, or repairing local billing state.
 */
public class AdminAuditDao {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT     = 500;

    private static final String SELECT_COLUMNS = """
            id, admin_user_id, target_user_id, action, entity_type, entity_id,
            old_value, new_value, reason, created_at
            """;

    private final DataSource ds;

    public AdminAuditDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public AdminAuditLogEntry record(
            UUID adminUserId,
            UUID targetUserId,
            String action,
            String entityType,
            String entityId,
            String oldValue,
            String newValue,
            String reason) throws SQLException {

        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId is required");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }

        UUID id = UUID.randomUUID();

        String sql = """
                INSERT INTO admin_audit_log
                    (id, admin_user_id, target_user_id, action, entity_type, entity_id,
                     old_value, new_value, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, adminUserId);
            ps.setObject(3, targetUserId);
            ps.setString(4, truncate(action.trim(), 128));
            ps.setString(5, truncate(blankToNull(entityType), 64));
            ps.setString(6, truncate(blankToNull(entityId), 128));
            ps.setString(7, truncate(oldValue, 4000));
            ps.setString(8, truncate(newValue, 4000));
            ps.setString(9, truncate(reason, 4000));
            ps.executeUpdate();
        }

        return findById(id)
                .orElseThrow(() -> new SQLException("Admin audit row was inserted but could not be re-read: " + id));
    }

    public List<AdminAuditLogEntry> recent(Integer limit) throws SQLException {
        String sql = """
                SELECT %s
                  FROM admin_audit_log
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """.formatted(SELECT_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, normalizeLimit(limit));

            try (ResultSet rs = ps.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    public List<AdminAuditLogEntry> forTargetUser(UUID targetUserId, Integer limit) throws SQLException {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        String sql = """
                SELECT %s
                  FROM admin_audit_log
                 WHERE target_user_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """.formatted(SELECT_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, targetUserId);
            ps.setInt(2, normalizeLimit(limit));

            try (ResultSet rs = ps.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    public List<AdminAuditLogEntry> byAdminUser(UUID adminUserId, Integer limit) throws SQLException {
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId is required");
        }

        String sql = """
                SELECT %s
                  FROM admin_audit_log
                 WHERE admin_user_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """.formatted(SELECT_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, adminUserId);
            ps.setInt(2, normalizeLimit(limit));

            try (ResultSet rs = ps.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    public java.util.Optional<AdminAuditLogEntry> findById(UUID id) throws SQLException {
        if (id == null) {
            return java.util.Optional.empty();
        }

        String sql = """
                SELECT %s
                  FROM admin_audit_log
                 WHERE id = ?
                """.formatted(SELECT_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? java.util.Optional.of(map(rs))
                        : java.util.Optional.empty();
            }
        }
    }

    private static List<AdminAuditLogEntry> mapList(ResultSet rs) throws SQLException {
        List<AdminAuditLogEntry> rows = new ArrayList<>();

        while (rs.next()) {
            rows.add(map(rs));
        }

        return rows;
    }

    private static AdminAuditLogEntry map(ResultSet rs) throws SQLException {
        return new AdminAuditLogEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("admin_user_id", UUID.class),
                rs.getObject("target_user_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}