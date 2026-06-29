package com.richardsand.novelkms.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserSubscriptionTest {

    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void activeHasAccess() {
        assertTrue(subscription("active", null, null).hasAccess(NOW));
    }

    @Test
    void activeCancelingHasAccess() {
        assertTrue(subscription("active_canceling", null, null).hasAccess(NOW));
    }

    @Test
    void familyHasAccess() {
        assertTrue(subscription("family", null, null).hasAccess(NOW));
    }

    @Test
    void trialingHasAccessBeforeTrialEnd() {
        assertTrue(subscription("trialing", null, NOW.plusSeconds(60)).hasAccess(NOW));
    }

    @Test
    void trialingDoesNotHaveAccessAtTrialEnd() {
        assertFalse(subscription("trialing", null, NOW).hasAccess(NOW));
    }

    @Test
    void trialingDoesNotHaveAccessAfterTrialEnd() {
        assertFalse(subscription("trialing", null, NOW.minusSeconds(1)).hasAccess(NOW));
    }

    @Test
    void trialingDoesNotHaveAccessWithoutTrialEnd() {
        assertFalse(subscription("trialing", null, null).hasAccess(NOW));
    }

    @Test
    void pastDueHasAccessBeforeCurrentPeriodEnd() {
        assertTrue(subscription("past_due", NOW.plusSeconds(60), null).hasAccess(NOW));
    }

    @Test
    void pastDueDoesNotHaveAccessAtCurrentPeriodEnd() {
        assertFalse(subscription("past_due", NOW, null).hasAccess(NOW));
    }

    @Test
    void pastDueDoesNotHaveAccessAfterCurrentPeriodEnd() {
        assertFalse(subscription("past_due", NOW.minusSeconds(1), null).hasAccess(NOW));
    }

    @Test
    void noneDoesNotHaveAccess() {
        assertFalse(subscription("none", null, null).hasAccess(NOW));
    }

    @Test
    void canceledDoesNotHaveAccess() {
        assertFalse(subscription("canceled", null, null).hasAccess(NOW));
    }

    @Test
    void unpaidDoesNotHaveAccess() {
        assertFalse(subscription("unpaid", null, null).hasAccess(NOW));
    }

    private static UserSubscription subscription(String status, Instant currentPeriodEnd, Instant trialEnd) {
        return new UserSubscription(
                UUID.randomUUID(),
                null,
                null,
                status,
                null,
                null,
                null,
                null,
                currentPeriodEnd,
                null,
                trialEnd,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }
}