package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.StripeWebhookEvent;

/**
 * Covers the idempotency contract {@link StripeWebhookResource} depends on:
 * {@code createReceivedIfAbsent} must accept an event exactly once and reject
 * (without throwing) every subsequent delivery of the same Stripe event id.
 */
class StripeWebhookEventDaoTest extends NovelKmsTestBase {

    private StripeWebhookEventDao dao;

    @BeforeEach
    void setUp() throws Exception {
        truncateAll();
        dao = new StripeWebhookEventDao(ds);
    }

    @Test
    void createReceivedIfAbsentAcceptsFirstDelivery() throws Exception {
        boolean firstDelivery = dao.createReceivedIfAbsent(
                "evt_first",
                "checkout.session.completed",
                false,
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                TEST_USER_ID,
                "cus_123",
                "sub_123");

        assertTrue(firstDelivery);

        StripeWebhookEvent stored = dao.findById("evt_first").orElseThrow();
        assertEquals("checkout.session.completed", stored.eventType());
        assertEquals("received", stored.processingStatus());
        assertEquals(TEST_USER_ID, stored.relatedUserId());
        assertEquals("cus_123", stored.relatedCustomerId());
        assertEquals("sub_123", stored.relatedSubscriptionId());
        assertNull(stored.processedAt());
    }

    @Test
    void createReceivedIfAbsentRejectsDuplicateDeliveryWithoutThrowing() throws Exception {
        dao.createReceivedIfAbsent("evt_dup", "invoice.payment_succeeded", true,
                Instant.now(), null, "cus_abc", "sub_abc");

        boolean secondDelivery = dao.createReceivedIfAbsent(
                "evt_dup", "invoice.payment_succeeded", true,
                Instant.now(), null, "cus_abc", "sub_abc");

        assertFalse(secondDelivery);
    }

    @Test
    void createReceivedIfAbsentAllowsNullRelatedFields() throws Exception {
        boolean firstDelivery = dao.createReceivedIfAbsent(
                "evt_unmapped", "customer.created", false,
                Instant.now(), null, null, null);

        assertTrue(firstDelivery);
        StripeWebhookEvent stored = dao.findById("evt_unmapped").orElseThrow();
        assertNull(stored.relatedUserId());
        assertNull(stored.relatedCustomerId());
        assertNull(stored.relatedSubscriptionId());
    }

    @Test
    void markProcessedSetsStatusAndClearsErrorMessage() throws Exception {
        dao.createReceivedIfAbsent("evt_proc", "customer.subscription.updated", false,
                Instant.now(), null, "cus_1", "sub_1");
        dao.markFailed("evt_proc", "boom", null, "cus_1", "sub_1");

        UUID userId = TEST_USER_ID;
        dao.markProcessed("evt_proc", userId, "cus_1", "sub_1");

        StripeWebhookEvent stored = dao.findById("evt_proc").orElseThrow();
        assertEquals("processed", stored.processingStatus());
        assertNull(stored.errorMessage());
        assertEquals(userId, stored.relatedUserId());
        assertEquals(stored.processedAt() != null, true);
    }

    @Test
    void markIgnoredRecordsReason() throws Exception {
        dao.createReceivedIfAbsent("evt_ignored", "customer.created", false,
                Instant.now(), null, null, null);

        dao.markIgnored("evt_ignored", "Unhandled Stripe event type: customer.created",
                null, null, null);

        StripeWebhookEvent stored = dao.findById("evt_ignored").orElseThrow();
        assertEquals("ignored", stored.processingStatus());
        assertEquals("Unhandled Stripe event type: customer.created", stored.errorMessage());
    }

    @Test
    void markFailedRecordsTruncatedErrorMessage() throws Exception {
        dao.createReceivedIfAbsent("evt_failed", "customer.subscription.updated", false,
                Instant.now(), null, "cus_2", "sub_2");

        String longMessage = "x".repeat(5000);
        dao.markFailed("evt_failed", longMessage, null, "cus_2", "sub_2");

        StripeWebhookEvent stored = dao.findById("evt_failed").orElseThrow();
        assertEquals("failed", stored.processingStatus());
        assertEquals(4000, stored.errorMessage().length());
    }

    @Test
    void findByIdReturnsEmptyForUnknownEvent() throws Exception {
        Optional<StripeWebhookEvent> result = dao.findById("evt_does_not_exist");
        assertTrue(result.isEmpty());
    }

    @Test
    void livemodeFlagIsPersisted() throws Exception {
        dao.createReceivedIfAbsent("evt_live", "checkout.session.completed", true,
                Instant.now(), null, "cus_live", "sub_live");

        StripeWebhookEvent stored = dao.findById("evt_live").orElseThrow();
        assertTrue(stored.livemode());
    }
}
