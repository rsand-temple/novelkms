package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.StripeWebhookEventDao;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.model.StripeWebhookEvent;
import com.richardsand.novelkms.model.UserSubscription;

import jakarta.ws.rs.core.Response;

/**
 * Covers {@link StripeWebhookResource}: HMAC signature verification, event
 * idempotency via {@link StripeWebhookEventDao}, and the subscription-state
 * mapping logic — including the item-level current_period_start/end fallback
 * and the cancel_at scheduled-cancellation normalization documented in
 * README.billing_and_subscriptions.md.
 *
 * <p>
 * These tests call {@link StripeWebhookResource#webhook(String, String)}
 * directly rather than through a JAX-RS test container — the resource has no
 * path/header-binding logic worth exercising through Jersey, and a plain
 * method call keeps the tests fast and focused on the parsing/mapping logic.
 */
class StripeWebhookResourceTest extends NovelKmsTestBase {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private UserSubscriptionDao   subscriptionDao;
    private StripeWebhookEventDao eventDao;
    private ObjectMapper          mapper;
    private StripeWebhookResource resource;

    @BeforeEach
    void setUp() throws Exception {
        truncateAll();
        subscriptionDao = new UserSubscriptionDao(ds);
        eventDao = new StripeWebhookEventDao(ds);
        mapper = createMapper();
        resource = new StripeWebhookResource(subscriptionDao, eventDao, mapper, configWithSecret(WEBHOOK_SECRET));
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    @Test
    void rejectsWebhookWhenNoSecretConfigured() throws Exception {
        StripeWebhookResource unconfigured = new StripeWebhookResource(
                subscriptionDao, eventDao, mapper, configWithSecret(null));

        String payload = checkoutSessionCompletedJson("evt_no_secret", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        Response response = unconfigured.webhook(payload, sign(payload, "irrelevant", now()));

        assertEquals(500, response.getStatus());
        assertEquals(Map.of("error", "stripe_webhook_not_configured"), response.getEntity());
    }

    @Test
    void rejectsMissingSignatureHeader() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_no_sig", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        Response response = resource.webhook(payload, null);

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_signature"), response.getEntity());
        assertTrue(eventDao.findById("evt_no_sig").isEmpty());
    }

    @Test
    void rejectsSignatureSignedWithWrongSecret() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_wrong_secret", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        Response response = resource.webhook(payload, sign(payload, "whsec_someone_elses_secret", now()));

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_signature"), response.getEntity());
    }

    @Test
    void rejectsSignatureForTamperedPayload() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_tampered", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        String signature = sign(payload, WEBHOOK_SECRET, now());
        String tamperedPayload = payload.replace("cus_1", "cus_attacker");

        Response response = resource.webhook(tamperedPayload, signature);

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_signature"), response.getEntity());
    }

    @Test
    void rejectsStaleTimestampOutsideTolerance() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_stale", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        long staleTimestamp = now() - 600L; // tolerance is 300 seconds
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, staleTimestamp));

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_signature"), response.getEntity());
    }

    @Test
    void acceptsValidSignature() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_valid_sig", "cs_1", "cus_1", "sub_1", TEST_USER_ID.toString());
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));

        assertEquals(200, response.getStatus());
    }

    // -------------------------------------------------------------------------
    // Payload validation
    // -------------------------------------------------------------------------

    @Test
    void rejectsMalformedJsonPayload() throws Exception {
        String payload = "{not valid json";
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_payload"), response.getEntity());
    }

    @Test
    void rejectsEventMissingIdAndType() throws Exception {
        String payload = "{\"livemode\":false}";
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "invalid_event"), response.getEntity());
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    void secondDeliveryOfSameEventIdIsIgnoredAsDuplicate() throws Exception {
        String firstPayload = checkoutSessionCompletedJson("evt_dup", "cs_1", "cus_original", "sub_original", TEST_USER_ID.toString());
        resource.webhook(firstPayload, sign(firstPayload, WEBHOOK_SECRET, now()));

        // Same event id, different body — the id alone must gate reprocessing.
        String secondPayload = checkoutSessionCompletedJson("evt_dup", "cs_1", "cus_attacker_replay", "sub_attacker_replay", TEST_USER_ID.toString());
        Response response = resource.webhook(secondPayload, sign(secondPayload, WEBHOOK_SECRET, now()));

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("received", true, "duplicate", true), response.getEntity());

        UserSubscription subscription = subscriptionDao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("cus_original", subscription.stripeCustomerId());
        assertEquals("sub_original", subscription.stripeSubscriptionId());
    }

    // -------------------------------------------------------------------------
    // checkout.session.completed
    // -------------------------------------------------------------------------

    @Test
    void checkoutSessionCompletedActivatesUserByClientReferenceId() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_checkout", "cs_1", "cus_new", "sub_new", TEST_USER_ID.toString());
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));

        assertEquals(200, response.getStatus());

        UserSubscription subscription = subscriptionDao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("active", subscription.status());
        assertEquals("cus_new", subscription.stripeCustomerId());
        assertEquals("sub_new", subscription.stripeSubscriptionId());

        StripeWebhookEvent stored = eventDao.findById("evt_checkout").orElseThrow();
        assertEquals("processed", stored.processingStatus());
        assertEquals(TEST_USER_ID, stored.relatedUserId());
    }

    @Test
    void checkoutSessionCompletedWithoutClientReferenceIdIsIgnoredGracefully() throws Exception {
        String payload = checkoutSessionCompletedJson("evt_checkout_no_ref", "cs_1", "cus_orphan", "sub_orphan", null);
        Response response = resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));

        assertEquals(200, response.getStatus());

        StripeWebhookEvent stored = eventDao.findById("evt_checkout_no_ref").orElseThrow();
        assertEquals("ignored", stored.processingStatus());
        assertNotNull(stored.errorMessage());
    }

    // -------------------------------------------------------------------------
    // customer.subscription.* — period resolution and status mapping
    // -------------------------------------------------------------------------

    @Test
    void subscriptionUpdatedUsesTopLevelCurrentPeriodFields() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_top", "sub_top", "trialing");

        ObjectNode subscription = subscriptionObject("sub_top", "cus_top", "active")
                .put("current_period_start", 1_751_000_000L)
                .put("current_period_end", 1_753_592_000L)
                .put("cancel_at_period_end", false);
        subscription.set("items", itemsWithPrice(null, null, "price_basic", "prod_basic"));

        Response response = send("evt_sub_top", "customer.subscription.updated", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByStripeSubscriptionId("sub_top").orElseThrow();
        assertEquals("active", updated.status());
        assertEquals(Instant.ofEpochSecond(1_751_000_000L), updated.currentPeriodStart());
        assertEquals(Instant.ofEpochSecond(1_753_592_000L), updated.currentPeriodEnd());
        assertEquals("price_basic", updated.stripePriceId());
        assertEquals("prod_basic", updated.stripeProductId());
    }

    @Test
    void subscriptionUpdatedFallsBackToItemLevelPeriodFieldsWhenTopLevelAbsent() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_item", "sub_item", "trialing");

        ObjectNode subscription = subscriptionObject("sub_item", "cus_item", "active")
                .put("cancel_at_period_end", false);
        subscription.set("items", itemsWithPrice(1_751_100_000L, 1_753_700_000L, "price_item", "prod_item"));

        Response response = send("evt_sub_item", "customer.subscription.updated", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByStripeSubscriptionId("sub_item").orElseThrow();
        assertEquals(Instant.ofEpochSecond(1_751_100_000L), updated.currentPeriodStart());
        assertEquals(Instant.ofEpochSecond(1_753_700_000L), updated.currentPeriodEnd());
    }

    @Test
    void subscriptionUpdatedFallsBackToCancelAtWhenNoPeriodEndAnywhere() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_cancelfallback", "sub_cancelfallback", "active");

        ObjectNode subscription = subscriptionObject("sub_cancelfallback", "cus_cancelfallback", "active")
                .put("cancel_at_period_end", true)
                .put("cancel_at", 1_785_000_000L);
        // No items array at all, and no top-level current_period_end.

        Response response = send("evt_sub_cancelfallback", "customer.subscription.updated", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByStripeSubscriptionId("sub_cancelfallback").orElseThrow();
        assertEquals(Instant.ofEpochSecond(1_785_000_000L), updated.currentPeriodEnd());
        assertEquals("active_canceling", updated.status());
    }

    /**
     * The exact payload shape from README.billing_and_subscriptions.md's
     * "Stripe may also represent a scheduled cancellation like this" example:
     * status stays "active" at Stripe's layer, but cancel_at + a non-null
     * canceled_at with no ended_at means the user has requested cancellation.
     * NovelKMS must normalize this to the local "active_canceling" status.
     */
    @Test
    void scheduledCancellationNormalizesToActiveCanceling() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_sched", "sub_sched", "active");

        ObjectNode subscription = subscriptionObject("sub_sched", "cus_sched", "active")
                .put("cancel_at", 1_785_254_700L)
                .put("cancel_at_period_end", false)
                .put("canceled_at", 1_782_681_965L)
                .putNull("ended_at");
        subscription.set("items", itemsWithPrice(null, 1_753_592_000L, "price_x", "prod_x"));

        Response response = send("evt_sched_cancel", "customer.subscription.updated", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByStripeSubscriptionId("sub_sched").orElseThrow();
        assertEquals("active_canceling", updated.status());
        assertTrue(updated.cancelAtPeriodEnd());
        assertEquals(Instant.ofEpochSecond(1_785_254_700L), updated.cancelAt());
        assertEquals(Instant.ofEpochSecond(1_753_592_000L), updated.currentPeriodEnd());
        assertEquals(Instant.ofEpochSecond(1_782_681_965L), updated.canceledAt());
    }

    @Test
    void cancellationDetailsAreCapturedFromSubscriptionEvent() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_reason", "sub_reason", "active");

        ObjectNode subscription = subscriptionObject("sub_reason", "cus_reason", "canceled")
                .put("cancel_at_period_end", false);
        ObjectNode cancellationDetails = subscription.putObject("cancellation_details");
        cancellationDetails.put("feedback", "too_expensive");
        cancellationDetails.put("comment", "Switching to a different tool");
        cancellationDetails.put("reason", "cancellation_requested");

        Response response = send("evt_cancel_reason", "customer.subscription.deleted", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByStripeSubscriptionId("sub_reason").orElseThrow();
        assertEquals("too_expensive", updated.cancellationFeedback());
        assertEquals("Switching to a different tool", updated.cancellationComment());
        assertEquals("cancellation_requested", updated.cancellationReason());
    }

    @Test
    void subscriptionEventDoesNotDemoteFamilyAccess() throws Exception {
        subscriptionDao.upsertStripeSubscription(new UserSubscriptionDao.StripeSubscriptionUpdate(
                TEST_USER_ID, "cus_family", "sub_family", "active", null, null, null,
                null, null, null, null, false, null, null, null, null, null, null, null));
        subscriptionDao.setFamilyAccess(TEST_USER_ID);

        ObjectNode subscription = subscriptionObject("sub_family", "cus_family", "canceled")
                .put("cancel_at_period_end", false);

        Response response = send("evt_family_untouched", "customer.subscription.deleted", subscription);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("family", updated.status());
    }

    @Test
    void subscriptionEventForUnknownCustomerAndSubscriptionIsIgnored() throws Exception {
        ObjectNode subscription = subscriptionObject("sub_unmapped", "cus_unmapped", "active")
                .put("cancel_at_period_end", false);

        Response response = send("evt_unmapped_sub", "customer.subscription.updated", subscription);

        assertEquals(200, response.getStatus());
        StripeWebhookEvent stored = eventDao.findById("evt_unmapped_sub").orElseThrow();
        assertEquals("ignored", stored.processingStatus());
        assertTrue(subscriptionDao.findByStripeSubscriptionId("sub_unmapped").isEmpty());
    }

    // -------------------------------------------------------------------------
    // invoice.payment_succeeded / invoice.payment_failed
    // -------------------------------------------------------------------------

    @Test
    void invoicePaymentSucceededRecordsTimestampAndClearsFailure() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_pay", "sub_pay", "active");
        subscriptionDao.markPaymentFailed("cus_pay", "sub_pay", Instant.now());

        ObjectNode invoice = mapper.createObjectNode();
        invoice.put("customer", "cus_pay");
        invoice.put("subscription", "sub_pay");
        invoice.put("created", now());

        Response response = send("evt_invoice_ok", "invoice.payment_succeeded", invoice);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByUserId(TEST_USER_ID).orElseThrow();
        assertNotNull(updated.lastPaymentSucceededAt());
        assertNull(updated.lastPaymentFailedAt());
    }

    @Test
    void invoicePaymentFailedSetsPastDueForNonFamilyUser() throws Exception {
        seedSubscription(TEST_USER_ID, "cus_fail", "sub_fail", "active");

        ObjectNode invoice = mapper.createObjectNode();
        invoice.put("customer", "cus_fail");
        invoice.put("subscription", "sub_fail");
        invoice.put("created", now());

        Response response = send("evt_invoice_fail", "invoice.payment_failed", invoice);
        assertEquals(200, response.getStatus());

        UserSubscription updated = subscriptionDao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("past_due", updated.status());
        assertNotNull(updated.lastPaymentFailedAt());
    }

    @Test
    void invoiceEventForUnknownSubscriptionIsIgnoredGracefully() throws Exception {
        ObjectNode invoice = mapper.createObjectNode();
        invoice.put("customer", "cus_ghost");
        invoice.put("subscription", "sub_ghost");
        invoice.put("created", now());

        Response response = send("evt_invoice_ghost", "invoice.payment_succeeded", invoice);

        assertEquals(200, response.getStatus());
        StripeWebhookEvent stored = eventDao.findById("evt_invoice_ghost").orElseThrow();
        assertEquals("ignored", stored.processingStatus());
    }

    // -------------------------------------------------------------------------
    // Unhandled event types
    // -------------------------------------------------------------------------

    @Test
    void unhandledEventTypeIsRecordedAndIgnoredWithoutError() throws Exception {
        ObjectNode object = mapper.createObjectNode();
        object.put("id", "cus_whatever");

        Response response = send("evt_unhandled", "customer.created", object);

        assertEquals(200, response.getStatus());
        StripeWebhookEvent stored = eventDao.findById("evt_unhandled").orElseThrow();
        assertEquals("ignored", stored.processingStatus());
        assertTrue(stored.errorMessage().contains("customer.created"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private NovelKmsConfig configWithSecret(String secret) throws Exception {
        String json = secret == null
                ? "{}"
                : "{\"billing\":{\"stripeWebhookSecret\":\"" + secret + "\"}}";
        ObjectMapper configMapper = mapper != null ? mapper : new ObjectMapper();
        return configMapper.readValue(json, NovelKmsConfig.class);
    }

    private void seedSubscription(UUID userId, String customerId, String subscriptionId, String status)
            throws Exception {
        subscriptionDao.upsertStripeSubscription(new UserSubscriptionDao.StripeSubscriptionUpdate(
                userId, customerId, subscriptionId, status, null, null, null,
                null, null, null, null, false, null, null, null, null, null, null, null));
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private String sign(String payload, String secret, long timestamp) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + hex;
    }

    /** Sends a {"data":{"object": object}} envelope with a valid signature. */
    private Response send(String eventId, String eventType, ObjectNode object) throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("id", eventId);
        event.put("type", eventType);
        event.put("livemode", false);
        event.put("created", now());
        event.putObject("data").set("object", object);

        String payload = event.toString();
        return resource.webhook(payload, sign(payload, WEBHOOK_SECRET, now()));
    }

    private ObjectNode subscriptionObject(String subscriptionId, String customerId, String status) {
        ObjectNode object = mapper.createObjectNode();
        object.put("id", subscriptionId);
        object.put("customer", customerId);
        object.put("status", status);
        return object;
    }

    private ObjectNode itemsWithPrice(Long periodStart, Long periodEnd, String priceId, String productId) {
        ObjectNode items = mapper.createObjectNode();
        ObjectNode item = mapper.createObjectNode();
        if (periodStart != null) {
            item.put("current_period_start", periodStart);
        }
        if (periodEnd != null) {
            item.put("current_period_end", periodEnd);
        }
        ObjectNode price = item.putObject("price");
        price.put("id", priceId);
        price.put("product", productId);
        items.putArray("data").add(item);
        return items;
    }

    private String checkoutSessionCompletedJson(
            String eventId, String sessionId, String customerId, String subscriptionId, String clientReferenceId)
            throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("id", eventId);
        event.put("type", "checkout.session.completed");
        event.put("livemode", false);
        event.put("created", now());

        ObjectNode session = mapper.createObjectNode();
        session.put("id", sessionId);
        session.put("customer", customerId);
        session.put("subscription", subscriptionId);
        if (clientReferenceId != null) {
            session.put("client_reference_id", clientReferenceId);
        }

        event.putObject("data").set("object", session);
        return event.toString();
    }
}
