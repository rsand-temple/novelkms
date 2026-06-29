package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.UserSubscriptionDao;
import com.richardsand.novelkms.model.UserSubscription;
import com.richardsand.novelkms.service.BillingService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * User-facing billing/entitlement surface.
 *
 * This is deliberately small for the first billing increment. Stripe is the
 * billing system of record, but NovelKMS authorizes requests using local state
 * populated by webhooks.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BillingResource {

    private static final Logger logger = LoggerFactory.getLogger(BillingResource.class);

    private final UserSubscriptionDao subscriptionDao;
    private BillingService            billingService;
    private final AuthDao             authDao;

    @Context
    ContainerRequestContext request;

    @Inject
    public BillingResource(UserSubscriptionDao subscriptionDao, BillingService billingService, AuthDao authDao) {
        this.subscriptionDao = subscriptionDao;
        this.billingService = billingService;
        this.authDao = authDao;
    }

    public record BillingStatusResponse(
            String status,
            boolean hasAccess,
            boolean familyAccess,
            boolean hasStripeCustomer,
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
            Instant updatedAt) {
    }

    /**
     * Current user's billing/access state.
     *
     * Frontend can call this during app bootstrap or from Settings -> Billing.
     */
    @GET
    @Path("/billing/status")
    public Response status() {
        UUID userId = CurrentUser.id(request);
        logger.debug("BillingResource.status invoked: userId={}", userId);

        return run(() -> {
            UserSubscription subscription = subscriptionDao.findByUserId(userId).orElse(null);
            if (subscription == null) {
                return Response.ok(new BillingStatusResponse(
                        "none",
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null)).build();
            }

            return Response.ok(toStatusResponse(subscription)).build();
        });
    }

    /**
     * Creates a Checkout Session for the authenticated user and returns the
     * Stripe-hosted checkout URL.
     */
    @POST
    @Path("/billing/checkout")
    public Response checkout() {
        UUID userId = CurrentUser.id(request);
        logger.info("BillingResource.checkout invoked: userId={}", userId);

        try {
            return Response.ok(Map.of("url", billingService.createCheckoutUrl(userId))).build();
        } catch (IllegalStateException e) {
            logger.warn("Billing checkout unavailable: {}", e.getMessage());
            return error(Status.BAD_REQUEST, "checkout_unavailable", e.getMessage());
        } catch (Exception e) {
            logger.error("Stripe checkout creation failed", e);
            return Response.serverError().entity(Map.of("error", "stripe_checkout_failed")).build();
        }
    }

    /**
     * Starts a local 14-day NovelKMS trial for a brand-new user.
     *
     * This does not call Stripe. It creates a local trialing entitlement row
     * that the SubscriptionAuthorizationFilter can immediately honor.
     */
    @POST
    @Path("/billing/trial")
    public Response startTrial() {
        UUID userId = CurrentUser.id(request);
        logger.info("BillingResource.startTrial invoked: userId={}", userId);

        return run(() -> {
            Instant now = Instant.now();

            AuthDao.UserTrialEligibility user = authDao.trialEligibilityUser(userId)
                    .orElse(null);

            if (user == null) {
                return error(Status.BAD_REQUEST,
                        "trial_unavailable",
                        "A free trial could not be started for this account.");
            }

            if (user.createdAt().isBefore(now.minus(24, ChronoUnit.HOURS))) {
                return error(Status.BAD_REQUEST,
                        "trial_unavailable",
                        "A free trial is only available immediately after creating a new NovelKMS account.");
            }

            Instant trialEnd = now.plus(14, ChronoUnit.DAYS);

            Optional<UserSubscription> subscription = subscriptionDao.startTrial(userId, now, trialEnd);
            if (subscription.isEmpty()) {
                UserSubscription existing = subscriptionDao.findByUserId(userId).orElse(null);
                if (existing != null) {
                    return error(Status.BAD_REQUEST,
                            "trial_unavailable",
                            "A free trial is only available for new NovelKMS accounts that have not already used a trial or subscription.");
                }

                return error(Status.BAD_REQUEST,
                        "trial_unavailable",
                        "A free trial could not be started for this account.");
            }

            return Response.ok(toStatusResponse(subscription.get())).build();
        });
    }

    /**
     * Creates a Stripe Billing Portal session for the authenticated user's existing Stripe customer ID.
     */
    @POST
    @Path("/billing/portal")
    public Response portal() {
        UUID userId = CurrentUser.id(request);
        logger.info("BillingResource.portal invoked: userId={}", userId);

        try {
            return Response.ok(Map.of("url", billingService.createPortalUrl(userId))).build();
        } catch (IllegalStateException e) {
            logger.warn("Billing portal unavailable: {}", e.getMessage());
            return error(Status.BAD_REQUEST, "portal_unavailable", e.getMessage());
        } catch (Exception e) {
            logger.error("Stripe portal creation failed", e);
            return Response.serverError().entity(Map.of("error", "stripe_portal_failed")).build();
        }
    }

    private static BillingStatusResponse toStatusResponse(UserSubscription subscription) {
        Instant now = Instant.now();
        return new BillingStatusResponse(
                subscription.status(),
                subscription.hasAccess(now),
                subscription.isFamilyAccess(),
                subscription.stripeCustomerId() != null && !subscription.stripeCustomerId().isBlank(),
                subscription.planKey(),
                subscription.stripePriceId(),
                subscription.stripeProductId(),
                subscription.currentPeriodStart(),
                subscription.currentPeriodEnd(),
                subscription.trialStart(),
                subscription.trialEnd(),
                subscription.cancelAtPeriodEnd(),
                subscription.canceledAt(),
                subscription.lastPaymentSucceededAt(),
                subscription.lastPaymentFailedAt(),
                subscription.updatedAt());
    }

    private static Response error(Status status, String code, String message) {
        return Response.status(status)
                .entity(Map.of("error", code, "message", message))
                .build();
    }

    private Response run(SqlCall call) {
        try {
            return call.call();
        } catch (SQLException e) {
            logger.error("Database error in BillingResource: {}", e.getMessage(), e);
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    private interface SqlCall {
        Response call() throws SQLException;
    }
}