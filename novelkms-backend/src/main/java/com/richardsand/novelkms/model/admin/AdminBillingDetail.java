package com.richardsand.novelkms.model.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminBillingDetail(
        UUID userId,
        String userStatus,
        AdminUserSubscriptionSummary subscription,
        boolean hasSubscriptionRow,
        boolean hasAccess,
        boolean familyAccess,
        boolean stripeLinked,
        boolean trialActive,
        boolean canceling,
        boolean paymentProblem,
        String accessReason,
        Instant evaluatedAt) {
}