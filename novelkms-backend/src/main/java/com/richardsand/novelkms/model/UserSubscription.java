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
        Instant canceledAt,
        Instant lastPaymentSucceededAt,
        Instant lastPaymentFailedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean hasAccess(Instant now) {
        if ("active".equals(status) || "trialing".equals(status) || "family".equals(status)) {
            return true;
        }

        /*
         * Optional grace behavior for failed renewals.
         *
         * The local row still records the real Stripe status, but the app may keep
         * access until the paid-through period actually ends.
         */
        if ("past_due".equals(status) && currentPeriodEnd != null && currentPeriodEnd.isAfter(now)) {
            return true;
        }

        return false;
    }

    public boolean isFamilyAccess() {
        return "family".equals(status);
    }
}