package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

public record UserSubscription(
        UUID userId,
        String stripeCustomerId,
        String stripeSubscriptionId,
        String status,
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
        Instant lastPaymentFailedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean hasAccess(Instant now) {
        if ("active".equals(status)
                || "active_canceling".equals(status)
                || "family".equals(status)) {
            return true;
        }

        if ("trialing".equals(status)) {
            return trialEnd != null && trialEnd.isAfter(now);
        }

        if ("past_due".equals(status) && currentPeriodEnd != null && currentPeriodEnd.isAfter(now)) {
            return true;
        }

        return false;
    }

    public boolean isFamilyAccess() {
        return "family".equals(status);
    }
}