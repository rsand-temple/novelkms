package com.richardsand.novelkms.dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.UserSubscription;

/**
 * Local billing/entitlement state.
 *
 * Stripe remains the billing system of record, but NovelKMS should authorize
 * requests from local subscription state populated by webhooks. The special
 * "family" status is a manual entitlement override and must not be demoted by
 * ordinary Stripe lifecycle events.
 */
public class UserSubscriptionDao {

    public record StripeSubscriptionUpdate(
            UUID userId,
            String stripeCustomerId,
            String stripeSubscriptionId,
            String stripeStatus,
            String planKey,
            String stripePriceId,
            String stripeProductId,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialStart,
            Instant trialEnd,
            boolean cancelAtPeriodEnd,
            Instant cancelAt,
            String cancellationFeedback,
            String cancellationComment,
            String cancellationReason,
            Instant canceledAt,
            Instant lastPaymentSucceededAt,
            Instant lastPaymentFailedAt) {
    }

    private static final String SELECT_COLUMNS = """
            user_id, stripe_customer_id, stripe_subscription_id, status, plan_key,
            stripe_price_id, stripe_product_id,
            current_period_start, current_period_end, trial_start, trial_end,
            cancel_at_period_end, cancel_at,
            cancellation_feedback, cancellation_comment, cancellation_reason,
            canceled_at,
            last_payment_succeeded_at, last_payment_failed_at,
            created_at, updated_at
            """;

    private final DataSource ds;

    public UserSubscriptionDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public Optional<UserSubscription> findByUserId(UUID userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM user_subscription WHERE user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<UserSubscription> findByStripeCustomerId(String stripeCustomerId) throws SQLException {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM user_subscription WHERE stripe_customer_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stripeCustomerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId) throws SQLException {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM user_subscription WHERE stripe_subscription_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stripeSubscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean hasAccess(UUID userId, Instant now) throws SQLException {
        return findByUserId(userId)
                .map(subscription -> subscription.hasAccess(now))
                .orElse(false);
    }

    public Optional<UserSubscription> setFamilyAccess(UUID userId) throws SQLException {
        return setManualStatus(userId, "family");
    }

    public Optional<UserSubscription> setManualStatus(UUID userId, String status) throws SQLException {
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus == null) {
            throw new IllegalArgumentException("status is required");
        }

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Optional<UserSubscription> existing = findByUserId(c, userId);
                if (existing.isPresent()) {
                    String update = """
                            UPDATE user_subscription
                               SET status = ?,
                                   updated_at = CURRENT_TIMESTAMP
                             WHERE user_id = ?
                            """;
                    try (PreparedStatement ps = c.prepareStatement(update)) {
                        ps.setString(1, normalizedStatus);
                        ps.setObject(2, userId);
                        ps.executeUpdate();
                    }
                } else {
                    String insert = """
                            INSERT INTO user_subscription
                                (user_id, status, created_at, updated_at)
                            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """;
                    try (PreparedStatement ps = c.prepareStatement(insert)) {
                        ps.setObject(1, userId);
                        ps.setString(2, normalizedStatus);
                        ps.executeUpdate();
                    }
                }

                c.commit();
                return findByUserId(userId);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Optional<UserSubscription> startTrial(UUID userId, Instant now, Instant trialEnd) throws SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (trialEnd == null || !trialEnd.isAfter(now)) {
            throw new IllegalArgumentException("trialEnd must be after now");
        }

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Optional<UserSubscription> existing = findByUserId(c, userId);
                if (existing.isPresent()) {
                    c.rollback();
                    return Optional.empty();
                }

                String insert = """
                        INSERT INTO user_subscription
                            (user_id, status, plan_key,
                             current_period_start, current_period_end,
                             trial_start, trial_end,
                             cancel_at_period_end,
                             created_at, updated_at)
                        VALUES (?, 'trialing', 'trial',
                                ?, ?,
                                ?, ?,
                                FALSE,
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """;

                try (PreparedStatement ps = c.prepareStatement(insert)) {
                    ps.setObject(1, userId);
                    ps.setTimestamp(2, toTimestamp(now));
                    ps.setTimestamp(3, toTimestamp(trialEnd));
                    ps.setTimestamp(4, toTimestamp(now));
                    ps.setTimestamp(5, toTimestamp(trialEnd));
                    ps.executeUpdate();
                }

                c.commit();
                return findByUserId(userId);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Optional<UserSubscription> upsertStripeSubscription(StripeSubscriptionUpdate update) throws SQLException {
        if (update == null || update.userId() == null) {
            throw new IllegalArgumentException("update.userId is required");
        }

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Optional<UserSubscription> existing = findByUserId(c, update.userId());
                if (existing.isPresent()) {
                    updateExistingStripeSubscription(c, existing.get(), update);
                } else {
                    insertStripeSubscription(c, update);
                }

                c.commit();
                return findByUserId(update.userId());
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Optional<UserSubscription> markPaymentSucceeded(
            String stripeCustomerId,
            String stripeSubscriptionId,
            Instant paymentTime) throws SQLException {

        Optional<UserSubscription> existing = findByStripeSubscriptionId(stripeSubscriptionId);
        if (existing.isEmpty()) {
            existing = findByStripeCustomerId(stripeCustomerId);
        }
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        String sql = """
                UPDATE user_subscription
                   SET last_payment_succeeded_at = ?,
                       last_payment_failed_at = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, toTimestamp(paymentTime));
            ps.setObject(2, existing.get().userId());
            ps.executeUpdate();
        }

        return findByUserId(existing.get().userId());
    }

    public Optional<UserSubscription> markPaymentFailed(
            String stripeCustomerId,
            String stripeSubscriptionId,
            Instant failureTime) throws SQLException {

        Optional<UserSubscription> existing = findByStripeSubscriptionId(stripeSubscriptionId);
        if (existing.isEmpty()) {
            existing = findByStripeCustomerId(stripeCustomerId);
        }
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        /*
         * Do not demote family/manual access on payment failure.
         */
        String sql = existing.get().isFamilyAccess()
                ? """
                        UPDATE user_subscription
                           SET last_payment_failed_at = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE user_id = ?
                        """
                : """
                        UPDATE user_subscription
                           SET status = 'past_due',
                               last_payment_failed_at = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE user_id = ?
                        """;

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, toTimestamp(failureTime));
            ps.setObject(2, existing.get().userId());
            ps.executeUpdate();
        }

        return findByUserId(existing.get().userId());
    }

    private void insertStripeSubscription(Connection c, StripeSubscriptionUpdate update) throws SQLException {
        String sql = """
                INSERT INTO user_subscription
                    (user_id, stripe_customer_id, stripe_subscription_id, status, plan_key,
                     stripe_price_id, stripe_product_id,
                     current_period_start, current_period_end, trial_start, trial_end,
                     cancel_at_period_end, cancel_at,
                     cancellation_feedback, cancellation_comment, cancellation_reason,
                     canceled_at,
                     last_payment_succeeded_at, last_payment_failed_at,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bindStripeSubscriptionUpdate(ps, update, update.stripeStatus(), 1);
            ps.executeUpdate();
        }
    }

    private void updateExistingStripeSubscription(
            Connection c,
            UserSubscription existing,
            StripeSubscriptionUpdate update) throws SQLException {

        /*
         * Preserve manual family access. Stripe identifiers and date metadata may
         * still be useful, but Stripe should not demote the entitlement status.
         */
        String effectiveStatus = existing.isFamilyAccess()
                ? existing.status()
                : update.stripeStatus();

        String sql = """
                UPDATE user_subscription
                   SET stripe_customer_id = ?,
                       stripe_subscription_id = ?,
                       status = ?,
                       plan_key = ?,
                       stripe_price_id = ?,
                       stripe_product_id = ?,
                       current_period_start = ?,
                       current_period_end = ?,
                       trial_start = ?,
                       trial_end = ?,
                       cancel_at_period_end = ?,
                       cancel_at = ?,
                       cancellation_feedback = ?,
                       cancellation_comment = ?,
                       cancellation_reason = ?,
                       canceled_at = ?,
                       last_payment_succeeded_at = ?,
                       last_payment_failed_at = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, blankToNull(update.stripeCustomerId()));
            ps.setString(i++, blankToNull(update.stripeSubscriptionId()));
            ps.setString(i++, effectiveStatus);
            ps.setString(i++, blankToNull(update.planKey()));
            ps.setString(i++, blankToNull(update.stripePriceId()));
            ps.setString(i++, blankToNull(update.stripeProductId()));
            ps.setTimestamp(i++, toTimestamp(update.currentPeriodStart()));
            ps.setTimestamp(i++, toTimestamp(update.currentPeriodEnd()));
            ps.setTimestamp(i++, toTimestamp(update.trialStart()));
            ps.setTimestamp(i++, toTimestamp(update.trialEnd()));
            ps.setBoolean(i++, update.cancelAtPeriodEnd());
            ps.setTimestamp(i++, toTimestamp(update.cancelAt()));
            ps.setString(i++, blankToNull(update.cancellationFeedback()));
            ps.setString(i++, blankToNull(update.cancellationComment()));
            ps.setString(i++, blankToNull(update.cancellationReason()));
            ps.setTimestamp(i++, toTimestamp(update.canceledAt()));
            ps.setTimestamp(i++, toTimestamp(update.lastPaymentSucceededAt()));
            ps.setTimestamp(i++, toTimestamp(update.lastPaymentFailedAt()));
            ps.setObject(i++, update.userId());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to update one user_subscription row for user_id "
                        + update.userId() + " but updated " + updated);
            }
        }
    }

    private void bindStripeSubscriptionUpdate(
            PreparedStatement ps,
            StripeSubscriptionUpdate update,
            String effectiveStatus,
            int startIndex) throws SQLException {

        int i = startIndex;
        ps.setObject(i++, update.userId());
        ps.setString(i++, blankToNull(update.stripeCustomerId()));
        ps.setString(i++, blankToNull(update.stripeSubscriptionId()));
        ps.setString(i++, blankToNull(effectiveStatus));
        ps.setString(i++, blankToNull(update.planKey()));
        ps.setString(i++, blankToNull(update.stripePriceId()));
        ps.setString(i++, blankToNull(update.stripeProductId()));
        ps.setTimestamp(i++, toTimestamp(update.currentPeriodStart()));
        ps.setTimestamp(i++, toTimestamp(update.currentPeriodEnd()));
        ps.setTimestamp(i++, toTimestamp(update.trialStart()));
        ps.setTimestamp(i++, toTimestamp(update.trialEnd()));
        ps.setBoolean(i++, update.cancelAtPeriodEnd());
        ps.setTimestamp(i++, toTimestamp(update.cancelAt()));
        ps.setString(i++, blankToNull(update.cancellationFeedback()));
        ps.setString(i++, blankToNull(update.cancellationComment()));
        ps.setString(i++, blankToNull(update.cancellationReason()));
        ps.setTimestamp(i++, toTimestamp(update.canceledAt()));
        ps.setTimestamp(i++, toTimestamp(update.lastPaymentSucceededAt()));
        ps.setTimestamp(i++, toTimestamp(update.lastPaymentFailedAt()));
    }

    private Optional<UserSubscription> findByUserId(Connection c, UUID userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM user_subscription WHERE user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static UserSubscription map(ResultSet rs) throws SQLException {
        return new UserSubscription(
                rs.getObject("user_id", UUID.class),
                rs.getString("stripe_customer_id"),
                rs.getString("stripe_subscription_id"),
                rs.getString("status"),
                rs.getString("plan_key"),
                rs.getString("stripe_price_id"),
                rs.getString("stripe_product_id"),
                toInstant(rs.getTimestamp("current_period_start")),
                toInstant(rs.getTimestamp("current_period_end")),
                toInstant(rs.getTimestamp("trial_start")),
                toInstant(rs.getTimestamp("trial_end")),
                rs.getBoolean("cancel_at_period_end"),
                toInstant(rs.getTimestamp("cancel_at")),
                rs.getString("cancellation_feedback"),
                rs.getString("cancellation_comment"),
                rs.getString("cancellation_reason"),
                toInstant(rs.getTimestamp("canceled_at")),
                toInstant(rs.getTimestamp("last_payment_succeeded_at")),
                toInstant(rs.getTimestamp("last_payment_failed_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
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
}