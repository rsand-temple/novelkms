package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.StripeWebhookEvent;

/**
 * Idempotency and audit table for Stripe webhook delivery.
 *
 * Stripe may retry events. The webhook resource should call
 * createReceivedIfAbsent(...) before doing any side effects. If it returns false,
 * the event has already been seen and should not be processed again.
 */
public class StripeWebhookEventDao {

    private static final String SELECT_COLUMNS = """
            stripe_event_id, event_type, livemode, stripe_created_at, received_at,
            processed_at, processing_status, error_message,
            related_user_id, related_customer_id, related_subscription_id
            """;

    private final DataSource ds;

    public StripeWebhookEventDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public Optional<StripeWebhookEvent> findById(String stripeEventId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM stripe_webhook_event WHERE stripe_event_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stripeEventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean createReceivedIfAbsent(
            String stripeEventId,
            String eventType,
            boolean livemode,
            Instant stripeCreatedAt,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        String sql = """
                INSERT INTO stripe_webhook_event
                    (stripe_event_id, event_type, livemode, stripe_created_at,
                     received_at, processing_status,
                     related_user_id, related_customer_id, related_subscription_id)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'received', ?, ?, ?)
                """;

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stripeEventId);
            ps.setString(2, eventType);
            ps.setBoolean(3, livemode);
            ps.setTimestamp(4, toTimestamp(stripeCreatedAt));
            ps.setObject(5, relatedUserId);
            ps.setString(6, blankToNull(relatedCustomerId));
            ps.setString(7, blankToNull(relatedSubscriptionId));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                return false;
            }
            throw e;
        }
    }

    public void markProcessed(
            String stripeEventId,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        String sql = """
                UPDATE stripe_webhook_event
                   SET processed_at = CURRENT_TIMESTAMP,
                       processing_status = 'processed',
                       error_message = NULL,
                       related_user_id = ?,
                       related_customer_id = ?,
                       related_subscription_id = ?
                 WHERE stripe_event_id = ?
                """;

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, relatedUserId);
            ps.setString(2, blankToNull(relatedCustomerId));
            ps.setString(3, blankToNull(relatedSubscriptionId));
            ps.setString(4, stripeEventId);
            ps.executeUpdate();
        }
    }

    public void markIgnored(
            String stripeEventId,
            String reason,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        String sql = """
                UPDATE stripe_webhook_event
                   SET processed_at = CURRENT_TIMESTAMP,
                       processing_status = 'ignored',
                       error_message = ?,
                       related_user_id = ?,
                       related_customer_id = ?,
                       related_subscription_id = ?
                 WHERE stripe_event_id = ?
                """;

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, truncate(reason, 4000));
            ps.setObject(2, relatedUserId);
            ps.setString(3, blankToNull(relatedCustomerId));
            ps.setString(4, blankToNull(relatedSubscriptionId));
            ps.setString(5, stripeEventId);
            ps.executeUpdate();
        }
    }

    public void markFailed(
            String stripeEventId,
            String errorMessage,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        String sql = """
                UPDATE stripe_webhook_event
                   SET processed_at = CURRENT_TIMESTAMP,
                       processing_status = 'failed',
                       error_message = ?,
                       related_user_id = ?,
                       related_customer_id = ?,
                       related_subscription_id = ?
                 WHERE stripe_event_id = ?
                """;

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, truncate(errorMessage, 4000));
            ps.setObject(2, relatedUserId);
            ps.setString(3, blankToNull(relatedCustomerId));
            ps.setString(4, blankToNull(relatedSubscriptionId));
            ps.setString(5, stripeEventId);
            ps.executeUpdate();
        }
    }

    private static StripeWebhookEvent map(ResultSet rs) throws SQLException {
        return new StripeWebhookEvent(
                rs.getString("stripe_event_id"),
                rs.getString("event_type"),
                rs.getBoolean("livemode"),
                toInstant(rs.getTimestamp("stripe_created_at")),
                toInstant(rs.getTimestamp("received_at")),
                toInstant(rs.getTimestamp("processed_at")),
                rs.getString("processing_status"),
                rs.getString("error_message"),
                rs.getObject("related_user_id", UUID.class),
                rs.getString("related_customer_id"),
                rs.getString("related_subscription_id"));
    }

    private static boolean isUniqueViolation(SQLException e) {
        SQLException current = e;
        while (current != null) {
            if ("23505".equals(current.getSQLState())) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
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