package com.richardsand.novelkms.auth;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.model.UserSubscription;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Enforces paid/family access after authentication.
 *
 * This filter intentionally allows the billing endpoints themselves so a logged
 * in but unsubscribed user can see status, start Checkout, or open the Stripe
 * portal.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class SubscriptionAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionAuthorizationFilter.class);

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "auth/",
            "healthcheck",
            "billing/stripe/webhook");

    private static final Set<String> BILLING_PREFIXES = Set.of(
            "billing/");

    private final UserSubscriptionDao subscriptionDao;
    private final NovelKmsConfig      config;

    @Inject
    public SubscriptionAuthorizationFilter(UserSubscriptionDao subscriptionDao, NovelKmsConfig config) {
        this.subscriptionDao = subscriptionDao;
        this.config = config;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String path = trim(request.getUriInfo().getPath());

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return;
        }

        if (!isEnforced()) {
            return;
        }

        /*
         * Allow logged-in users to query billing status, start Checkout, and open
         * the Stripe portal even if they do not currently have access.
         */
        if (BILLING_PREFIXES.stream().anyMatch(path::startsWith)) {
            return;
        }
        
        if (path.startsWith("admin/") && CurrentUser.isAdmin(request)) {
            return;
        }

        UUID userId = CurrentUser.id(request);

        try {
            UserSubscription subscription = subscriptionDao.findByUserId(userId).orElse(null);

            if (subscription != null && subscription.hasAccess(Instant.now())) {
                logger.debug("User {} has valid subscription for {}", userId, path);
                return;
            }

            logger.debug("Subscription access denied: userId={}, path={}, status={}",
                    userId,
                    path,
                    subscription == null ? "none" : subscription.status());

            request.abortWith(Response.status(402)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", "subscription_required",
                            "message", "An active NovelKMS subscription is required.",
                            "status", subscription == null ? "none" : subscription.status()))
                    .build());
        } catch (SQLException e) {
            logger.error("Database error in SubscriptionAccessFilter: {}", e.getMessage(), e);
            request.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "server_error"))
                    .build());
        }
    }

    private boolean isEnforced() {
        return config.getBilling() != null && config.getBilling().enforceSubscriptions;
    }

    private static String trim(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
