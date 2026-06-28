package com.richardsand.novelkms.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.UserSubscriptionDao;
import com.richardsand.novelkms.model.UserSubscription;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

public class BillingService {

    private final UserSubscriptionDao    subscriptionDao;
    private final NovelKmsConfig.Billing billing;

    public BillingService(UserSubscriptionDao subscriptionDao, NovelKmsConfig config) {
        this.subscriptionDao = subscriptionDao;
        this.billing = config.getBilling();
    }

    public String createCheckoutUrl(UUID userId) throws Exception {
        requireBillingConfig();

        Stripe.apiKey = billing.stripeSecretKey;

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(userId.toString())
                .setSuccessUrl(billing.successUrl)
                .setCancelUrl(billing.cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(billing.stripePriceId)
                        .setQuantity(1L)
                        .build());

        /*
         * If this user already has a Stripe customer ID, reuse it so Stripe does
         * not create duplicate customer records.
         */
        UserSubscription existing = subscriptionDao.findByUserId(userId).orElse(null);
        if (existing != null && existing.stripeCustomerId() != null && !existing.stripeCustomerId().isBlank()) {
            builder.setCustomer(existing.stripeCustomerId());
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("novelkms_user_id", userId.toString());

        builder.putMetadata("novelkms_user_id", userId.toString());

        Session session = Session.create(builder.build());
        return session.getUrl();
    }

    public String createPortalUrl(UUID userId) throws Exception {
        requireBillingConfig();

        UserSubscription existing = subscriptionDao.findByUserId(userId).orElse(null);
        if (existing == null || existing.stripeCustomerId() == null || existing.stripeCustomerId().isBlank()) {
            throw new IllegalStateException("No Stripe customer exists for this user.");
        }

        Stripe.apiKey = billing.stripeSecretKey;

        com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(existing.stripeCustomerId())
                .setReturnUrl(billing.successUrl)
                .build();

        com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);

        return session.getUrl();
    }

    private void requireBillingConfig() {
        if (billing == null
                || isBlank(billing.stripeSecretKey)
                || isBlank(billing.stripePriceId)
                || isBlank(billing.successUrl)
                || isBlank(billing.cancelUrl)) {
            throw new IllegalStateException("Stripe billing is not fully configured.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}