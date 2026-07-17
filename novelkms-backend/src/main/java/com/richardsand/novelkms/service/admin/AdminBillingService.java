package com.richardsand.novelkms.service.admin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
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
import com.richardsand.novelkms.model.admin.ExtendTrialRequest;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

public class AdminBillingService {

    public static final String ACTION_GRANT_FAMILY_ACCESS = "GRANT_FAMILY_ACCESS";
    public static final String ACTION_EXTEND_TRIAL        = "EXTEND_TRIAL";
    public static final String ENTITY_USER_SUBSCRIPTION   = "user_subscription";

    /** Longest a trial may be extended to, measured from now. */
    static final int MAX_TRIAL_DAYS_FROM_NOW = 365;

    /**
     * Statuses that already confer access at least as strong as a trial. Extending
     * a trial over any of these would be a demotion: {@code family} is a manual
     * override that must never be demoted, and {@code active}/{@code active_canceling}
     * are live Stripe entitlements Stripe owns.
     */
    private static final Set<String> NON_DEMOTABLE_STATUSES =
            Set.of("family", "active", "active_canceling");

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

    public UserSubscription extendTrial(
            UUID adminUserId,
            UUID targetUserId,
            ExtendTrialRequest request) throws SQLException {

        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId is required");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }
        if (request == null) {
            throw new BadRequestException("Provide either trialEndsAt or extendDays");
        }

        Instant now = Instant.now();

        // Conservative, same as grant-family: only mutate entitlement for active users.
        if (authDao.trialEligibilityUser(targetUserId).isEmpty()) {
            throw new NotFoundException("Target user not found or inactive");
        }

        UserSubscription oldSubscription = userSubscriptionDao.findByUserId(targetUserId).orElse(null);

        String currentStatus = oldSubscription == null ? null : oldSubscription.status();
        if (currentStatus != null && NON_DEMOTABLE_STATUSES.contains(currentStatus)) {
            throw new BadRequestException(
                    "Cannot extend a trial for a user with status '" + currentStatus
                            + "'. Extending would demote a stronger entitlement.");
        }

        Instant currentTrialEnd = oldSubscription == null ? null : oldSubscription.trialEnd();
        Instant resolvedTrialEnd = resolveTrialEnd(request, now, currentTrialEnd);

        UserSubscription newSubscription = userSubscriptionDao.extendTrial(targetUserId, now, resolvedTrialEnd)
                .orElseThrow(() -> new SQLException(
                        "Trial extension completed but subscription row could not be re-read"));

        adminAuditDao.record(
                adminUserId,
                targetUserId,
                ACTION_EXTEND_TRIAL,
                ENTITY_USER_SUBSCRIPTION,
                targetUserId.toString(),
                toJson(oldSubscription),
                toJson(newSubscription),
                extendTrialAuditReason(request, resolvedTrialEnd));

        return newSubscription;
    }

    /**
     * Resolves an {@link ExtendTrialRequest} into a single concrete UTC trial-end
     * instant, validating that exactly one mode was supplied and that the result is
     * in the future and within {@link #MAX_TRIAL_DAYS_FROM_NOW}.
     */
    Instant resolveTrialEnd(ExtendTrialRequest request, Instant now, Instant currentTrialEnd) {
        boolean hasDate = request.trialEndsAt() != null;
        boolean hasDays = request.extendDays() != null;

        if (hasDate == hasDays) {
            throw new BadRequestException("Provide exactly one of trialEndsAt or extendDays");
        }

        Instant maxEnd = now.plus(Duration.ofDays(MAX_TRIAL_DAYS_FROM_NOW));
        Instant resolved;

        if (hasDays) {
            int days = request.extendDays();
            if (days <= 0) {
                throw new BadRequestException("extendDays must be a positive number of days");
            }
            // Anchor on the later of now and any live trial end so extending never
            // shortens an existing future trial.
            Instant anchor = (currentTrialEnd != null && currentTrialEnd.isAfter(now))
                    ? currentTrialEnd
                    : now;
            resolved = anchor.plus(Duration.ofDays(days));
        } else {
            // Absolute date: resolve a plain date to end-of-day UTC to avoid an
            // off-by-one where a picker's midnight would expire access that same day.
            Instant requested = request.trialEndsAt();
            LocalDate date = requested.atZone(ZoneOffset.UTC).toLocalDate();
            resolved = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        if (!resolved.isAfter(now)) {
            throw new BadRequestException("Trial end must be in the future");
        }
        if (resolved.isAfter(maxEnd)) {
            throw new BadRequestException(
                    "Trial end may be at most " + MAX_TRIAL_DAYS_FROM_NOW + " days from now");
        }

        return resolved;
    }

    private String extendTrialAuditReason(ExtendTrialRequest request, Instant resolvedTrialEnd) {
        String base = request.extendDays() != null
                ? "Trial extended by " + request.extendDays() + " day(s) to " + resolvedTrialEnd
                : "Trial extended to " + resolvedTrialEnd;
        return auditReason(base, request.reason(), request.note());
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
        String combined = auditReason(null, reason, note);
        return combined == null ? "Family access granted" : combined;
    }

    /**
     * Joins an optional machine-generated {@code base} description with an
     * admin-supplied {@code reason} and {@code note} into a single audit string.
     * Returns null only when all three are blank.
     */
    private String auditReason(String base, String reason, String note) {
        String normalizedBase   = blankToNull(base);
        String normalizedReason = blankToNull(reason);
        String normalizedNote   = blankToNull(note);

        StringBuilder sb = new StringBuilder();
        appendPart(sb, normalizedBase);
        appendPart(sb, normalizedReason);
        appendPart(sb, normalizedNote);

        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" - ");
        }
        sb.append(part);
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