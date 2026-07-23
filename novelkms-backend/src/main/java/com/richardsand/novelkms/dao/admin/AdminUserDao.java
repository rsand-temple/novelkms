package com.richardsand.novelkms.dao.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.model.admin.AdminUserSubscriptionSummary;
import com.richardsand.novelkms.model.admin.AdminUserSummary;
import com.richardsand.novelkms.model.admin.AdminUserUsageSummary;

/**
 * Read-only admin/support lookup DAO for users.
 *
 * Mutating admin operations should live in narrower admin services/resources and
 * must write admin_audit_log entries.
 */
public class AdminUserDao {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT     = 200;

    private static final String USER_COLUMNS = """
            u.id, u.email_address, u.normalized_email, u.email_verified,
            u.first_name, u.last_name, u.display_name, u.mobile_number,
            u.status, u.created_at, u.updated_at, u.last_login_at
            """;

    private static final String SUBSCRIPTION_COLUMNS = """
            us.status AS subscription_status,
            us.plan_key AS subscription_plan_key,
            us.stripe_customer_id AS subscription_stripe_customer_id,
            us.stripe_subscription_id AS subscription_stripe_subscription_id,
            us.stripe_price_id AS subscription_stripe_price_id,
            us.stripe_product_id AS subscription_stripe_product_id,
            us.current_period_start AS subscription_current_period_start,
            us.current_period_end AS subscription_current_period_end,
            us.trial_start AS subscription_trial_start,
            us.trial_end AS subscription_trial_end,
            us.cancel_at_period_end AS subscription_cancel_at_period_end,
            us.cancel_at AS subscription_cancel_at,
            us.cancellation_feedback AS subscription_cancellation_feedback,
            us.cancellation_comment AS subscription_cancellation_comment,
            us.cancellation_reason AS subscription_cancellation_reason,
            us.canceled_at AS subscription_canceled_at,
            us.last_payment_succeeded_at AS subscription_last_payment_succeeded_at,
            us.last_payment_failed_at AS subscription_last_payment_failed_at,
            us.created_at AS subscription_created_at,
            us.updated_at AS subscription_updated_at
            """;

    private final DataSource ds;

    public AdminUserDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public List<AdminUserSummary> search(String query, Integer limit) throws SQLException {
        int    resolvedLimit   = normalizeLimit(limit);
        String normalizedQuery = blankToNull(query);

        if (normalizedQuery == null) {
            return recent(resolvedLimit);
        }

        UUID   queryUuid = parseUuid(normalizedQuery);
        String like      = "%" + normalizedQuery.toLowerCase() + "%";

        String sql = """
                SELECT %s,
                       %s
                  FROM app_user u
                  LEFT JOIN user_subscription us ON us.user_id = u.id
                 WHERE lower(u.email_address) LIKE ?
                    OR lower(u.normalized_email) LIKE ?
                    OR lower(COALESCE(u.display_name, '')) LIKE ?
                    OR lower(COALESCE(u.first_name, '')) LIKE ?
                    OR lower(COALESCE(u.last_name, '')) LIKE ?
                    OR lower(COALESCE(us.stripe_customer_id, '')) LIKE ?
                    OR lower(COALESCE(us.stripe_subscription_id, '')) LIKE ?
                    OR (? IS NOT NULL AND u.id = ?)
                 ORDER BY u.created_at DESC, u.id DESC
                 LIMIT ?
                """.formatted(USER_COLUMNS, SUBSCRIPTION_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setString(i++, like);
            ps.setObject(i++, queryUuid);
            ps.setObject(i++, queryUuid);
            ps.setInt(i++, resolvedLimit);

            try (ResultSet rs = ps.executeQuery()) {
                List<AdminUserSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    UUID userId = rs.getObject("id", UUID.class);
                    rows.add(mapSummary(c, rs, userId));
                }
                return rows;
            }
        }
    }

    public Optional<AdminUserDetail> findById(UUID userId) throws SQLException {
        if (userId == null) {
            return Optional.empty();
        }

        String sql = """
                SELECT %s,
                       %s
                  FROM app_user u
                  LEFT JOIN user_subscription us ON us.user_id = u.id
                 WHERE u.id = ?
                """.formatted(USER_COLUMNS, SUBSCRIPTION_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapDetail(c, rs, userId));
            }
        }
    }

    private List<AdminUserSummary> recent(int limit) throws SQLException {
        String sql = """
                SELECT %s,
                       %s
                  FROM app_user u
                  LEFT JOIN user_subscription us ON us.user_id = u.id
                 ORDER BY u.created_at DESC, u.id DESC
                 LIMIT ?
                """.formatted(USER_COLUMNS, SUBSCRIPTION_COLUMNS);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<AdminUserSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    UUID userId = rs.getObject("id", UUID.class);
                    rows.add(mapSummary(c, rs, userId));
                }
                return rows;
            }
        }
    }

    private AdminUserSummary mapSummary(Connection c, ResultSet rs, UUID userId) throws SQLException {
        return new AdminUserSummary(
                userId,
                rs.getString("email_address"),
                rs.getString("normalized_email"),
                rs.getBoolean("email_verified"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("display_name"),
                rs.getString("mobile_number"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("last_login_at")),
                roles(c, userId),
                subscription(rs),
                usage(c, userId));
    }

    private AdminUserDetail mapDetail(Connection c, ResultSet rs, UUID userId) throws SQLException {
        return new AdminUserDetail(
                userId,
                rs.getString("email_address"),
                rs.getString("normalized_email"),
                rs.getBoolean("email_verified"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("display_name"),
                rs.getString("mobile_number"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("last_login_at")),
                roles(c, userId),
                subscription(rs),
                usage(c, userId));
    }

    private Set<String> roles(Connection c, UUID userId) throws SQLException {
        String sql = """
                SELECT role
                  FROM user_role
                 WHERE user_id = ?
                 ORDER BY role
                """;

        Set<String> roles = new LinkedHashSet<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    if (role != null && !role.isBlank()) {
                        roles.add(role.trim());
                    }
                }
            }
        }

        return Set.copyOf(roles);
    }

    private AdminUserSubscriptionSummary subscription(ResultSet rs) throws SQLException {
        String status = rs.getString("subscription_status");

        if (status == null) {
            return null;
        }

        return new AdminUserSubscriptionSummary(
                status,
                rs.getString("subscription_plan_key"),
                rs.getString("subscription_stripe_customer_id"),
                rs.getString("subscription_stripe_subscription_id"),
                rs.getString("subscription_stripe_price_id"),
                rs.getString("subscription_stripe_product_id"),
                toInstant(rs.getTimestamp("subscription_current_period_start")),
                toInstant(rs.getTimestamp("subscription_current_period_end")),
                toInstant(rs.getTimestamp("subscription_trial_start")),
                toInstant(rs.getTimestamp("subscription_trial_end")),
                rs.getBoolean("subscription_cancel_at_period_end"),
                toInstant(rs.getTimestamp("subscription_cancel_at")),
                rs.getString("subscription_cancellation_feedback"),
                rs.getString("subscription_cancellation_comment"),
                rs.getString("subscription_cancellation_reason"),
                toInstant(rs.getTimestamp("subscription_canceled_at")),
                toInstant(rs.getTimestamp("subscription_last_payment_succeeded_at")),
                toInstant(rs.getTimestamp("subscription_last_payment_failed_at")),
                toInstant(rs.getTimestamp("subscription_created_at")),
                toInstant(rs.getTimestamp("subscription_updated_at")));
    }

    private AdminUserUsageSummary usage(Connection c, UUID userId) throws SQLException {
        return new AdminUserUsageSummary(
                count(c, "SELECT COUNT(*) FROM project WHERE owner_user_id = ? AND deleted_at IS NULL", userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM book b
                          JOIN project p ON p.id = b.project_id
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                           AND b.deleted_at IS NULL
                        """, userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM part pt
                          JOIN book b ON b.id = pt.book_id
                          JOIN project p ON p.id = b.project_id
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                           AND b.deleted_at IS NULL
                        """, userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM chapter ch
                          LEFT JOIN book b ON b.id = ch.book_id
                          LEFT JOIN codex cx ON cx.id = ch.codex_id
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          LEFT JOIN book sb ON sb.id = ch.scratchpad_book_id
                          JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                           AND ch.deleted_at IS NULL
                        """, userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM scene s
                          JOIN chapter ch ON ch.id = s.chapter_id
                          LEFT JOIN book b ON b.id = ch.book_id
                          LEFT JOIN codex cx ON cx.id = ch.codex_id
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          LEFT JOIN book sb ON sb.id = ch.scratchpad_book_id
                          JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                           AND ch.deleted_at IS NULL
                           AND s.deleted_at IS NULL
                        """, userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM codex cx
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          JOIN project p ON p.id = COALESCE(cx.project_id, cb.project_id)
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                        """, userId),
                count(c, """
                        SELECT COUNT(*)
                          FROM ai_review ar
                          JOIN chapter ch ON ch.id = ar.chapter_id
                          LEFT JOIN book b ON b.id = ch.book_id
                          LEFT JOIN codex cx ON cx.id = ch.codex_id
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          LEFT JOIN book sb ON sb.id = ch.scratchpad_book_id
                          JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)
                         WHERE p.owner_user_id = ?
                           AND p.deleted_at IS NULL
                           AND ar.deleted_at IS NULL
                        """, userId));
    }

    private static int count(Connection c, String sql, UUID userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}