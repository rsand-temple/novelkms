package com.richardsand.novelkms.model.admin;

import java.time.Instant;

public record AdminOverviewMetrics(
        Instant evaluatedAt,
        UserMetrics users,
        BillingMetrics billing,
        ActivityMetrics activity,
        ContentMetrics content,
        AiMetrics ai,
        BillingHealthMetrics billingHealth) {

    public record UserMetrics(
            int total,
            int active,
            int disabled,
            int createdLast7Days,
            int createdLast30Days,
            int idle30Days,
            int neverLoggedIn) {
    }

    public record BillingMetrics(
            int subscribedAccess,
            int active,
            int activeCanceling,
            int trialing,
            int pastDue,
            int family,
            int canceled,
            int unpaid,
            int paused,
            int incomplete,
            int incompleteExpired,
            int none,
            int noSubscriptionRow) {
    }

    public record ActivityMetrics(
            int loginsLast24Hours,
            int loginsLast7Days,
            int loginsLast30Days) {
    }

    public record ContentMetrics(
            int projects,
            int books,
            int parts,
            int chapters,
            int scenes,
            int codexEntries) {
    }

    public record AiMetrics(
            int reviewsTotal,
            int reviewsLast7Days,
            int reviewsLast30Days,
            int openRecommendations,
            int deferredRecommendations,
            int promotedRecommendations) {
    }

    public record BillingHealthMetrics(
            int failedWebhookEventsLast7Days,
            int unprocessedWebhookEvents,
            int paymentProblems) {
    }
}