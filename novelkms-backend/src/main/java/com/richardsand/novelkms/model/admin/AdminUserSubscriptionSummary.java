package com.richardsand.novelkms.model.admin;

import java.time.Instant;

public record AdminUserSubscriptionSummary(
        String status,
        String planKey,
        String stripeCustomerId,
        String stripeSubscriptionId,
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
}