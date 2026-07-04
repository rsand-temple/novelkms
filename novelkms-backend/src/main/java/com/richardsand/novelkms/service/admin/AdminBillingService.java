package com.richardsand.novelkms.service.admin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.admin.AdminUserDao;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.model.UserSubscription;
import com.richardsand.novelkms.model.admin.AdminBillingDetail;
import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.model.admin.AdminUserSubscriptionSummary;

import jakarta.ws.rs.NotFoundException;

public class AdminBillingService {

    public static final String ACTION_GRANT_FAMILY_ACCESS = "GRANT_FAMILY_ACCESS";
    public static final String ENTITY_USER_SUBSCRIPTION   = "user_subscription";

    private final UserSubscriptionDao userSubscriptionDao;
    private final AdminAuditDao       adminAuditDao;
    private final AdminUserDao        adminUserDao;
    private final AuthDao             authDao;
    private final ObjectMapper        objectMapper;

    public AdminBillingService(
            UserSubscriptionDao userSubscriptionDao,
            AdminAuditDao adminAuditDao,
            AdminUserDao adminUserDao,
            AuthDao authDao,
            ObjectMapper objectMapper) {

        this.userSubscriptionDao = userSubscriptionDao;
        this.adminAuditDao = adminAuditDao;
        this.adminUserDao = adminUserDao;
        this.authDao = authDao;
        this.objectMapper = objectMapper;
    }

    public AdminBillingDetail billingDetail(UUID targetUserId) throws SQLException {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        AdminUserDetail user = adminUserDao.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        UserSubscription subscription = userSubscriptionDao.findByUserId(targetUserId).orElse(null);

        return billingDetail(user, subscription, Instant.now());
    }

    public UserSubscription grantFamilyAccess(
            UUID adminUserId,
            UUID targetUserId,
            String reason,
            String note) throws SQLException {

        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId is required");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        /*
         * For the first mutation, only allow active users. Admin read-only billing detail
         * can inspect inactive users, but entitlement mutation should stay conservative.
         */
        if (authDao.trialEligibilityUser(targetUserId).isEmpty()) {
            throw new NotFoundException("Target user not found or inactive");
        }

        UserSubscription oldSubscription = userSubscriptionDao.findByUserId(targetUserId).orElse(null);
        UserSubscription newSubscription = userSubscriptionDao.setFamilyAccess(targetUserId)
                .orElseThrow(() -> new SQLException("Family access update completed but subscription row could not be re-read"));

        adminAuditDao.record(
                adminUserId,
                targetUserId,
                ACTION_GRANT_FAMILY_ACCESS,
                ENTITY_USER_SUBSCRIPTION,
                targetUserId.toString(),
                toJson(oldSubscription),
                toJson(newSubscription),
                auditReason(reason, note));

        return newSubscription;
    }

    private AdminBillingDetail billingDetail(
            AdminUserDetail user,
            UserSubscription subscription,
            Instant now) {

        boolean hasSubscriptionRow = subscription != null;
        boolean hasAccess          = subscription != null && subscription.hasAccess(now);
        boolean familyAccess       = subscription != null && subscription.isFamilyAccess();
        boolean stripeLinked       = subscription != null
                && (blankToNull(subscription.stripeCustomerId()) != null
                        || blankToNull(subscription.stripeSubscriptionId()) != null);
        boolean trialActive        = subscription != null
                && "trialing".equals(subscription.status())
                && subscription.trialEnd() != null
                && subscription.trialEnd().isAfter(now);
        boolean canceling          = subscription != null
                && ("active_canceling".equals(subscription.status()) || subscription.cancelAtPeriodEnd());
        boolean paymentProblem     = subscription != null
                && ("past_due".equals(subscription.status())
                        || "unpaid".equals(subscription.status())
                        || subscription.lastPaymentFailedAt() != null);

        return new AdminBillingDetail(
                user.id(),
                user.status(),
                toSummary(subscription),
                hasSubscriptionRow,
                hasAccess,
                familyAccess,
                stripeLinked,
                trialActive,
                canceling,
                paymentProblem,
                accessReason(subscription, hasAccess, now),
                now);
    }

    private String accessReason(UserSubscription subscription, boolean hasAccess, Instant now) {
        if (subscription == null) {
            return "none";
        }

        if ("family".equals(subscription.status())) {
            return "family";
        }

        if ("active".equals(subscription.status())) {
            return "active";
        }

        if ("active_canceling".equals(subscription.status())) {
            return "active_canceling";
        }

        if ("trialing".equals(subscription.status())) {
            if (subscription.trialEnd() != null && subscription.trialEnd().isAfter(now)) {
                return "trial_active";
            }
            return "trial_expired";
        }

        if ("past_due".equals(subscription.status())
                && subscription.currentPeriodEnd() != null
                && subscription.currentPeriodEnd().isAfter(now)) {
            return "past_due_grace";
        }

        return hasAccess ? subscription.status() : "no_access";
    }

    private AdminUserSubscriptionSummary toSummary(UserSubscription subscription) {
        if (subscription == null) {
            return null;
        }

        return new AdminUserSubscriptionSummary(
                subscription.status(),
                subscription.planKey(),
                subscription.stripeCustomerId(),
                subscription.stripeSubscriptionId(),
                subscription.stripePriceId(),
                subscription.stripeProductId(),
                subscription.currentPeriodStart(),
                subscription.currentPeriodEnd(),
                subscription.trialStart(),
                subscription.trialEnd(),
                subscription.cancelAtPeriodEnd(),
                subscription.cancelAt(),
                subscription.cancellationFeedback(),
                subscription.cancellationComment(),
                subscription.cancellationReason(),
                subscription.canceledAt(),
                subscription.lastPaymentSucceededAt(),
                subscription.lastPaymentFailedAt(),
                subscription.createdAt(),
                subscription.updatedAt());
    }

    private String auditReason(String reason, String note) {
        String normalizedReason = blankToNull(reason);
        String normalizedNote   = blankToNull(note);

        if (normalizedReason == null && normalizedNote == null) {
            return "Family access granted";
        }
        if (normalizedReason == null) {
            return normalizedNote;
        }
        if (normalizedNote == null) {
            return normalizedReason;
        }

        return normalizedReason + " - " + normalizedNote;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}