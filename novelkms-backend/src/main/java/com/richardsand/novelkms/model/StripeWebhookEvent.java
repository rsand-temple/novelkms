package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

public record StripeWebhookEvent(
        String stripeEventId,
        String eventType,
        boolean livemode,
        Instant stripeCreatedAt,
        Instant receivedAt,
        Instant processedAt,
        String processingStatus,
        String errorMessage,
        UUID relatedUserId,
        String relatedCustomerId,
        String relatedSubscriptionId) {
}