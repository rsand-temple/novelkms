package com.richardsand.novelkms.resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.StripeWebhookEventDao;
import com.richardsand.novelkms.dao.UserSubscriptionDao;
import com.richardsand.novelkms.dao.UserSubscriptionDao.StripeSubscriptionUpdate;
import com.richardsand.novelkms.model.UserSubscription;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Stripe webhook receiver.
 *
 * This resource intentionally does not use the Stripe SDK yet. It verifies
 * Stripe's HMAC signature directly, parses the event JSON, records idempotency
 * in stripe_webhook_event, and updates user_subscription.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class StripeWebhookResource {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookResource.class);

    private static final long SIGNATURE_TOLERANCE_SECONDS = 300L;

    private final UserSubscriptionDao   subscriptionDao;
    private final StripeWebhookEventDao eventDao;
    private final ObjectMapper          mapper;
    private final String                webhookSecret;

    @Inject
    public StripeWebhookResource(
            UserSubscriptionDao subscriptionDao,
            StripeWebhookEventDao eventDao,
            ObjectMapper mapper,
            NovelKmsConfig config) {

        this.subscriptionDao = subscriptionDao;
        this.eventDao = eventDao;
        this.mapper = mapper;
        this.webhookSecret = config.getBilling() == null ? null : config.getBilling().stripeWebhookSecret;
    }

    @POST
    @Path("/billing/stripe/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response webhook(String payload, @HeaderParam("Stripe-Signature") String signatureHeader) {
        logger.debug("Stripe webhook invoked");
        if (isBlank(webhookSecret)) {
            logger.error("Stripe webhook received but no webhook secret is configured");
            return error(Status.INTERNAL_SERVER_ERROR, "stripe_webhook_not_configured");
        }

        if (!verifyStripeSignature(payload, signatureHeader, webhookSecret)) {
            logger.warn("Rejected Stripe webhook with invalid signature");
            return error(Status.BAD_REQUEST, "invalid_signature");
        }

        try {
            JsonNode event           = mapper.readTree(payload);
            String   eventId         = text(event, "id");
            String   eventType       = text(event, "type");
            boolean  livemode        = bool(event, "livemode");
            Instant  stripeCreatedAt = unixInstant(event.path("created"));

            if (isBlank(eventId) || isBlank(eventType)) {
                return error(Status.BAD_REQUEST, "invalid_event");
            }

            JsonNode object                = event.path("data").path("object");
            String   relatedCustomerId     = extractCustomerId(object);
            String   relatedSubscriptionId = extractSubscriptionId(eventType, object);
            UUID     relatedUserId         = extractUserId(eventType, object, relatedCustomerId, relatedSubscriptionId).orElse(null);

            boolean firstDelivery = eventDao.createReceivedIfAbsent(
                    eventId,
                    eventType,
                    livemode,
                    stripeCreatedAt,
                    relatedUserId,
                    relatedCustomerId,
                    relatedSubscriptionId);

            if (!firstDelivery) {
                logger.info("Ignoring duplicate Stripe webhook event {}", eventId);
                return Response.ok(Map.of("received", true, "duplicate", true)).build();
            }

            try {
                ProcessResult result = processEvent(eventType, object, relatedUserId, relatedCustomerId, relatedSubscriptionId);

                if (result.ignored()) {
                    eventDao.markIgnored(eventId, result.message(), result.userId(), result.customerId(), result.subscriptionId());
                } else {
                    eventDao.markProcessed(eventId, result.userId(), result.customerId(), result.subscriptionId());
                }

                return Response.ok(Map.of("received", true)).build();
            } catch (Exception e) {
                logger.error("Failed processing Stripe webhook event {} type {}", eventId, eventType, e);
                eventDao.markFailed(
                        eventId,
                        e.getMessage(),
                        relatedUserId,
                        relatedCustomerId,
                        relatedSubscriptionId);
                return error(Status.INTERNAL_SERVER_ERROR, "webhook_processing_failed");
            }
        } catch (Exception e) {
            logger.error("Invalid Stripe webhook payload", e);
            return error(Status.BAD_REQUEST, "invalid_payload");
        }
    }

    private ProcessResult processEvent(
            String eventType,
            JsonNode object,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        return switch (eventType) {
        case "checkout.session.completed" ->
            handleCheckoutSessionCompleted(object, relatedUserId, relatedCustomerId, relatedSubscriptionId);

        case "customer.subscription.created",
                "customer.subscription.updated",
                "customer.subscription.deleted" ->
            handleSubscriptionEvent(object, relatedUserId, relatedCustomerId, relatedSubscriptionId);

        case "invoice.payment_succeeded" ->
            handleInvoicePaymentSucceeded(object, relatedUserId, relatedCustomerId, relatedSubscriptionId);

        case "invoice.payment_failed" ->
            handleInvoicePaymentFailed(object, relatedUserId, relatedCustomerId, relatedSubscriptionId);

        default -> new ProcessResult(
                true,
                "Unhandled Stripe event type: " + eventType,
                relatedUserId,
                relatedCustomerId,
                relatedSubscriptionId);
        };
    }

    private ProcessResult handleCheckoutSessionCompleted(
            JsonNode session,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        UUID userId = relatedUserId;
        if (userId == null) {
            Optional<UUID> fromClientReference = uuidFromText(text(session, "client_reference_id"));
            if (fromClientReference.isPresent()) {
                userId = fromClientReference.get();
            }
        }

        if (userId == null) {
            return new ProcessResult(
                    true,
                    "Checkout session did not include a NovelKMS user id",
                    null,
                    relatedCustomerId,
                    relatedSubscriptionId);
        }

        /*
         * This is enough to activate the user immediately after successful hosted
         * checkout. Later subscription.created/updated events will refine period,
         * plan, and cancellation metadata.
         */
        subscriptionDao.upsertStripeSubscription(new StripeSubscriptionUpdate(
                userId,
                relatedCustomerId,
                relatedSubscriptionId,
                "active",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                Instant.now(),
                null));

        return new ProcessResult(false, null, userId, relatedCustomerId, relatedSubscriptionId);
    }

    private ProcessResult handleSubscriptionEvent(
            JsonNode subscription,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        UUID userId = relatedUserId;
        if (userId == null) {
            Optional<UserSubscription> existing = Optional.empty();

            if (!isBlank(relatedSubscriptionId)) {
                existing = subscriptionDao.findByStripeSubscriptionId(relatedSubscriptionId);
            }
            if (existing.isEmpty() && !isBlank(relatedCustomerId)) {
                existing = subscriptionDao.findByStripeCustomerId(relatedCustomerId);
            }

            if (existing.isPresent()) {
                userId = existing.get().userId();
            }
        }

        if (userId == null) {
            return new ProcessResult(
                    true,
                    "Subscription event could not be mapped to a NovelKMS user",
                    null,
                    relatedCustomerId,
                    relatedSubscriptionId);
        }

        String status = text(subscription, "status");
        if (isBlank(status)) {
            status = "canceled";
        }

        PriceInfo price = firstPrice(subscription);

        subscriptionDao.upsertStripeSubscription(new StripeSubscriptionUpdate(
                userId,
                relatedCustomerId,
                relatedSubscriptionId,
                status,
                price.planKey(),
                price.priceId(),
                price.productId(),
                unixInstant(subscription.path("current_period_start")),
                unixInstant(subscription.path("current_period_end")),
                unixInstant(subscription.path("trial_start")),
                unixInstant(subscription.path("trial_end")),
                bool(subscription, "cancel_at_period_end"),
                unixInstant(subscription.path("canceled_at")),
                null,
                null));

        return new ProcessResult(false, null, userId, relatedCustomerId, relatedSubscriptionId);
    }

    private ProcessResult handleInvoicePaymentSucceeded(
            JsonNode invoice,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        Optional<UserSubscription> updated = subscriptionDao.markPaymentSucceeded(
                relatedCustomerId,
                relatedSubscriptionId,
                unixInstant(invoice.path("created")));

        return updated
                .map(subscription -> new ProcessResult(false, null, subscription.userId(), relatedCustomerId, relatedSubscriptionId))
                .orElseGet(() -> new ProcessResult(
                        true,
                        "Payment succeeded event could not be mapped to a NovelKMS subscription",
                        relatedUserId,
                        relatedCustomerId,
                        relatedSubscriptionId));
    }

    private ProcessResult handleInvoicePaymentFailed(
            JsonNode invoice,
            UUID relatedUserId,
            String relatedCustomerId,
            String relatedSubscriptionId) throws SQLException {

        Optional<UserSubscription> updated = subscriptionDao.markPaymentFailed(
                relatedCustomerId,
                relatedSubscriptionId,
                unixInstant(invoice.path("created")));

        return updated
                .map(subscription -> new ProcessResult(false, null, subscription.userId(), relatedCustomerId, relatedSubscriptionId))
                .orElseGet(() -> new ProcessResult(
                        true,
                        "Payment failed event could not be mapped to a NovelKMS subscription",
                        relatedUserId,
                        relatedCustomerId,
                        relatedSubscriptionId));
    }

    private Optional<UUID> extractUserId(
            String eventType,
            JsonNode object,
            String customerId,
            String subscriptionId) throws SQLException {

        if ("checkout.session.completed".equals(eventType)) {
            Optional<UUID> clientReference = uuidFromText(text(object, "client_reference_id"));
            if (clientReference.isPresent()) {
                return clientReference;
            }
        }

        if (!isBlank(subscriptionId)) {
            Optional<UserSubscription> existing = subscriptionDao.findByStripeSubscriptionId(subscriptionId);
            if (existing.isPresent()) {
                return Optional.of(existing.get().userId());
            }
        }

        if (!isBlank(customerId)) {
            Optional<UserSubscription> existing = subscriptionDao.findByStripeCustomerId(customerId);
            if (existing.isPresent()) {
                return Optional.of(existing.get().userId());
            }
        }

        return Optional.empty();
    }

    private static String extractCustomerId(JsonNode object) {
        JsonNode customer = object.path("customer");
        if (customer.isTextual()) {
            return customer.asText();
        }
        if (customer.isObject()) {
            return text(customer, "id");
        }
        return null;
    }

    private static String extractSubscriptionId(String eventType, JsonNode object) {
        if (eventType.startsWith("customer.subscription.")) {
            return text(object, "id");
        }

        JsonNode subscription = object.path("subscription");
        if (subscription.isTextual()) {
            return subscription.asText();
        }
        if (subscription.isObject()) {
            return text(subscription, "id");
        }

        return null;
    }

    private static PriceInfo firstPrice(JsonNode subscription) {
        JsonNode price = subscription
                .path("items")
                .path("data")
                .path(0)
                .path("price");

        if (!price.isObject()) {
            return new PriceInfo(null, null, null);
        }

        String   priceId   = text(price, "id");
        String   productId = null;
        JsonNode product   = price.path("product");
        if (product.isTextual()) {
            productId = product.asText();
        } else if (product.isObject()) {
            productId = text(product, "id");
        }

        /*
         * planKey is intentionally not inferred yet. You can map price IDs to
         * application plan names later through config.
         */
        return new PriceInfo(null, priceId, productId);
    }

    private static boolean verifyStripeSignature(String payload, String signatureHeader, String secret) {
        if (isBlank(payload) || isBlank(signatureHeader) || isBlank(secret)) {
            return false;
        }

        String timestamp         = null;
        String expectedSignature = null;

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("v1".equals(kv[0])) {
                expectedSignature = kv[1];
            }
        }

        if (isBlank(timestamp) || isBlank(expectedSignature)) {
            return false;
        }

        try {
            long signatureTime = Long.parseLong(timestamp);
            long now           = Instant.now().getEpochSecond();
            if (Math.abs(now - signatureTime) > SIGNATURE_TOLERANCE_SECONDS) {
                return false;
            }

            String signedPayload   = timestamp + "." + payload;
            String actualSignature = hmacSha256Hex(secret, signedPayload);

            return MessageDigest.isEqual(
                    actualSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacSha256Hex(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static Response error(Status status, String code) {
        return Response.status(status).entity(Map.of("error", code)).build();
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean bool(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static Instant unixInstant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        long epochSeconds = node.asLong(0L);
        return epochSeconds <= 0L ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private static Optional<UUID> uuidFromText(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProcessResult(
            boolean ignored,
            String message,
            UUID userId,
            String customerId,
            String subscriptionId) {
    }

    private record PriceInfo(
            String planKey,
            String priceId,
            String productId) {
    }
}