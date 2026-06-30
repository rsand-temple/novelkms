package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.admin.AdminOverviewMetrics;

public class AdminMetricsDao {

    private final DataSource ds;

    public AdminMetricsDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public AdminOverviewMetrics overview() throws SQLException {
        Instant now   = Instant.now();
        Instant day1  = now.minus(Duration.ofDays(1));
        Instant day7  = now.minus(Duration.ofDays(7));
        Instant day30 = now.minus(Duration.ofDays(30));

        try (Connection c = ds.getConnection()) {
            return new AdminOverviewMetrics(
                    now,
                    userMetrics(c, day7, day30),
                    billingMetrics(c),
                    activityMetrics(c, day1, day7, day30),
                    contentMetrics(c),
                    aiMetrics(c, day7, day30),
                    billingHealthMetrics(c, day7));
        }
    }

    private AdminOverviewMetrics.UserMetrics userMetrics(Connection c, Instant day7, Instant day30)
            throws SQLException {

        return new AdminOverviewMetrics.UserMetrics(
                count(c, "SELECT COUNT(*) FROM app_user"),
                count(c, "SELECT COUNT(*) FROM app_user WHERE status = 'ACTIVE'"),
                count(c, "SELECT COUNT(*) FROM app_user WHERE status <> 'ACTIVE'"),
                count(c, "SELECT COUNT(*) FROM app_user WHERE created_at >= ?", day7),
                count(c, "SELECT COUNT(*) FROM app_user WHERE created_at >= ?", day30),
                count(c, """
                        SELECT COUNT(*)
                          FROM app_user
                         WHERE status = 'ACTIVE'
                           AND last_login_at IS NOT NULL
                           AND last_login_at < ?
                        """, day30),
                count(c, """
                        SELECT COUNT(*)
                          FROM app_user
                         WHERE status = 'ACTIVE'
                           AND last_login_at IS NULL
                        """));
    }

    private AdminOverviewMetrics.BillingMetrics billingMetrics(Connection c) throws SQLException {
        int active            = countStatus(c, "active");
        int activeCanceling   = countStatus(c, "active_canceling");
        int trialing          = countStatus(c, "trialing");
        int pastDue           = countStatus(c, "past_due");
        int family            = countStatus(c, "family");
        int canceled          = countStatus(c, "canceled");
        int unpaid            = countStatus(c, "unpaid");
        int paused            = countStatus(c, "paused");
        int incomplete        = countStatus(c, "incomplete");
        int incompleteExpired = countStatus(c, "incomplete_expired");
        int none              = countStatus(c, "none");

        int subscribedAccess = active + activeCanceling + trialing + family;

        int noSubscriptionRow = count(c, """
                SELECT COUNT(*)
                  FROM app_user u
                 WHERE NOT EXISTS (
                       SELECT 1
                         FROM user_subscription us
                        WHERE us.user_id = u.id
                 )
                """);

        return new AdminOverviewMetrics.BillingMetrics(
                subscribedAccess,
                active,
                activeCanceling,
                trialing,
                pastDue,
                family,
                canceled,
                unpaid,
                paused,
                incomplete,
                incompleteExpired,
                none,
                noSubscriptionRow);
    }

    private AdminOverviewMetrics.ActivityMetrics activityMetrics(
            Connection c,
            Instant day1,
            Instant day7,
            Instant day30) throws SQLException {

        return new AdminOverviewMetrics.ActivityMetrics(
                count(c, "SELECT COUNT(*) FROM app_user WHERE last_login_at >= ?", day1),
                count(c, "SELECT COUNT(*) FROM app_user WHERE last_login_at >= ?", day7),
                count(c, "SELECT COUNT(*) FROM app_user WHERE last_login_at >= ?", day30));
    }

    private AdminOverviewMetrics.ContentMetrics contentMetrics(Connection c) throws SQLException {
        return new AdminOverviewMetrics.ContentMetrics(
                count(c, "SELECT COUNT(*) FROM project WHERE deleted_at IS NULL"),
                count(c, """
                        SELECT COUNT(*)
                          FROM book b
                          JOIN project p ON p.id = b.project_id
                         WHERE p.deleted_at IS NULL
                           AND b.deleted_at IS NULL
                        """),
                count(c, """
                        SELECT COUNT(*)
                          FROM part pt
                          JOIN book b ON b.id = pt.book_id
                          JOIN project p ON p.id = b.project_id
                         WHERE p.deleted_at IS NULL
                           AND b.deleted_at IS NULL
                        """),
                count(c, """
                        SELECT COUNT(*)
                          FROM chapter ch
                          LEFT JOIN book b ON b.id = ch.book_id
                          LEFT JOIN codex cx ON cx.id = ch.codex_id
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id)
                         WHERE p.deleted_at IS NULL
                           AND ch.deleted_at IS NULL
                        """),
                count(c, """
                        SELECT COUNT(*)
                          FROM scene s
                          JOIN chapter ch ON ch.id = s.chapter_id
                          LEFT JOIN book b ON b.id = ch.book_id
                          LEFT JOIN codex cx ON cx.id = ch.codex_id
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id)
                         WHERE p.deleted_at IS NULL
                           AND ch.deleted_at IS NULL
                           AND s.deleted_at IS NULL
                        """),
                count(c, """
                        SELECT COUNT(*)
                          FROM codex cx
                          LEFT JOIN book cb ON cb.id = cx.book_id
                          JOIN project p ON p.id = COALESCE(cx.project_id, cb.project_id)
                         WHERE p.deleted_at IS NULL
                        """));
    }

    private AdminOverviewMetrics.AiMetrics aiMetrics(Connection c, Instant day7, Instant day30)
            throws SQLException {

        return new AdminOverviewMetrics.AiMetrics(
                count(c, "SELECT COUNT(*) FROM ai_review WHERE deleted_at IS NULL"),
                count(c, "SELECT COUNT(*) FROM ai_review WHERE deleted_at IS NULL AND submitted_at >= ?", day7),
                count(c, "SELECT COUNT(*) FROM ai_review WHERE deleted_at IS NULL AND submitted_at >= ?", day30),
                countRecommendationStatus(c, "OPEN"),
                countRecommendationStatus(c, "DEFERRED"),
                countRecommendationStatus(c, "PROMOTED"));
    }

    private AdminOverviewMetrics.BillingHealthMetrics billingHealthMetrics(Connection c, Instant day7)
            throws SQLException {

        return new AdminOverviewMetrics.BillingHealthMetrics(
                count(c, """
                        SELECT COUNT(*)
                          FROM stripe_webhook_event
                         WHERE processing_status = 'failed'
                           AND received_at >= ?
                        """, day7),
                count(c, """
                        SELECT COUNT(*)
                          FROM stripe_webhook_event
                         WHERE processing_status IN ('received', 'failed')
                        """),
                count(c, """
                        SELECT COUNT(*)
                          FROM user_subscription
                         WHERE status IN ('past_due', 'unpaid', 'incomplete', 'incomplete_expired')
                        """));
    }

    private int countStatus(Connection c, String status) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM user_subscription WHERE status = ?", status);
    }

    private int countRecommendationStatus(Connection c, String status) throws SQLException {
        return count(c, """
                SELECT COUNT(*)
                  FROM ai_review_recommendation r
                  JOIN ai_review ar ON ar.id = r.review_id
                 WHERE ar.deleted_at IS NULL
                   AND r.status = ?
                """, status);
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int count(Connection c, String sql, Instant value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(value));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int count(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}