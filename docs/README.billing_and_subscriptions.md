* # NovelKMS Billing and Subscription Management

  This document summarizes the Stripe subscription implementation added to NovelKMS. It covers the current architecture, database model, backend resources, webhook behavior, subscription enforcement, frontend billing UI, and known next steps.

  ## Current Billing Design

  NovelKMS uses Stripe for payment collection and subscription lifecycle management, but NovelKMS keeps a local subscription/entitlement table for authorization.

  Stripe remains the billing system of record. NovelKMS uses Stripe webhooks to synchronize subscription state into the local database. Application access decisions are made from the local `user_subscription` row.

  The current implementation supports:

  - Stripe Checkout for new subscriptions.
  - Stripe Customer Portal for cancellation and account billing management.
  - Stripe webhook processing with idempotency.
  - Local subscription enforcement with a configuration kill switch.
  - A manual `family` entitlement status.
  - Active-but-canceling subscriptions.
  - Cancellation feedback/comment capture.
  - Frontend Billing settings tab.
  - Friendly Stripe success/cancel return pages.
  - Global frontend handling of `402 subscription_required`.

  ## Stripe Objects Used

  NovelKMS currently uses these Stripe concepts:

  - Checkout Session
  - Customer
  - Subscription
  - Subscription Item
  - Invoice
  - Customer Portal Session
  - Webhook Events

  The hosted Checkout flow is initiated from the authenticated NovelKMS backend, not from a static Stripe Payment Link. This is important because the backend sets the NovelKMS user id into Stripe Checkout metadata and `client_reference_id`.

  ## Configuration

  Billing is configured in `config.yaml` under `billing`.

  Example:

  ```yaml
  billing:
    stripeSecretKey: ${STRIPE_SECRET_KEY}
    stripeWebhookSecret: ${STRIPE_WEBHOOK_SECRET}
    stripePriceId: ${STRIPE_PRICE_ID}
    successUrl: https://novelkms.com/billing/success
    cancelUrl: https://novelkms.com/billing/cancel
    enforceSubscriptions: false
  ```

  Environment variables are stored in the container environment file, currently under the production Podman deployment configuration.

  Important variables:

  ```bash
  STRIPE_SECRET_KEY=sk_test_...
  STRIPE_WEBHOOK_SECRET=whsec_...
  STRIPE_PRICE_ID=price_...
  ```

  The webhook endpoint configured in Stripe is:

  ```text
  https://novelkms.com/api/billing/stripe/webhook
  ```

  The success and cancel URLs are frontend routes and should not be prefixed with `/api`:

  ```text
  https://novelkms.com/billing/success
  https://novelkms.com/billing/cancel
  ```

  ## Stripe Dashboard Setup

  The Stripe webhook endpoint should send at least these events:

  ```text
  checkout.session.completed
  customer.subscription.created
  customer.subscription.updated
  customer.subscription.deleted
  invoice.payment_succeeded
  invoice.payment_failed
  ```

  The Stripe Customer Portal should allow at least:

  - Cancel subscriptions.
  - Update payment methods.
  - View invoice history.

  ## Database Model

  Two billing tables were added.

  ### `user_subscription`

  This table stores the local entitlement state for each NovelKMS user.

  Key columns include:

  ```text
  user_id
  stripe_customer_id
  stripe_subscription_id
  status
  plan_key
  stripe_price_id
  stripe_product_id
  current_period_start
  current_period_end
  trial_start
  trial_end
  cancel_at_period_end
  cancel_at
  cancellation_feedback
  cancellation_comment
  cancellation_reason
  canceled_at
  last_payment_succeeded_at
  last_payment_failed_at
  created_at
  updated_at
  ```

  Current allowed statuses:

  ```text
  none
  incomplete
  incomplete_expired
  trialing
  active
  active_canceling
  past_due
  canceled
  unpaid
  paused
  family
  ```

  The special `family` status is a manual entitlement override. Stripe webhook events may update Stripe metadata on the row, but should not demote a `family` user’s entitlement.

  The `active_canceling` status means:

  ```text
  The user still has access, but has requested cancellation and the subscription is scheduled to end.
  ```

  ### `stripe_webhook_event`

  This table records received Stripe webhook events for idempotency and audit.

  Key columns include:

  ```text
  stripe_event_id
  event_type
  livemode
  stripe_created_at
  received_at
  processed_at
  processing_status
  error_message
  related_user_id
  related_customer_id
  related_subscription_id
  ```

  Allowed processing statuses:

  ```text
  received
  processed
  ignored
  failed
  ```

  Duplicate Stripe events are ignored by checking `stripe_event_id`.

  ## Backend Components

  ### `BillingResource`

  Provides authenticated billing endpoints:

  ```text
  GET  /api/billing/status
  POST /api/billing/checkout
  POST /api/billing/portal
  ```

  `/api/billing/status` returns the current local subscription/entitlement state.

  `/api/billing/checkout` creates a Stripe Checkout Session and redirects the user to Stripe-hosted checkout.

  `/api/billing/portal` creates a Stripe Customer Portal Session and redirects the user to Stripe-hosted billing management.

  ### `BillingService`

  Encapsulates Stripe SDK calls for:

  - Creating Checkout Sessions.
  - Creating Customer Portal Sessions.

  Checkout Sessions include:

  ```java
  .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
  .setClientReferenceId(userId.toString())
  .putMetadata("novelkms_user_id", userId.toString())
  .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
          .putMetadata("novelkms_user_id", userId.toString())
          .build())
  ```

  The `client_reference_id` is especially important for `checkout.session.completed`, because it lets the webhook map the Stripe checkout back to a NovelKMS user.

  Subscription metadata is used to improve later mapping of subscription lifecycle events.

  ### `StripeWebhookResource`

  Receives Stripe webhook events at:

  ```text
  /api/billing/stripe/webhook
  ```

  The resource:

  - Verifies Stripe webhook signatures using the configured `STRIPE_WEBHOOK_SECRET`.
  - Parses the event JSON.
  - Inserts the event into `stripe_webhook_event`.
  - Skips duplicate event ids.
  - Processes known event types.
  - Marks events as `processed`, `ignored`, or `failed`.

  Handled event types:

  ```text
  checkout.session.completed
  customer.subscription.created
  customer.subscription.updated
  customer.subscription.deleted
  invoice.payment_succeeded
  invoice.payment_failed
  ```

  ### Important Stripe Subscription Parsing Detail

  Stripe subscription payloads may not provide `current_period_start` and `current_period_end` at the top level. In the tested payload, these were located under the first subscription item:

  ```json
  items.data[0].current_period_start
  items.data[0].current_period_end
  ```

  NovelKMS now resolves subscription periods by checking:

  1. Top-level subscription period fields.
  2. First subscription item period fields.
  3. `cancel_at` as a fallback for period end.

  Stripe may also represent a scheduled cancellation like this:

  ```json
  {
    "status": "active",
    "cancel_at": 1785254700,
    "cancel_at_period_end": false,
    "canceled_at": 1782681965,
    "ended_at": null
  }
  ```

  NovelKMS interprets this as local status:

  ```text
  active_canceling
  ```

  and sets:

  ```text
  cancel_at_period_end = true
  cancel_at = Stripe cancel_at
  current_period_end = subscription item current_period_end or cancel_at
  canceled_at = cancellation request timestamp
  ```

  Cancellation details are preserved from:

  ```json
  cancellation_details.feedback
  cancellation_details.comment
  cancellation_details.reason
  ```

  ## DAO Layer

  ### `UserSubscriptionDao`

  `UserSubscriptionDao` manages local subscription state.

  Important behaviors:

  - `upsertStripeSubscription(...)` creates or updates a user subscription row.
  - Existing `family` entitlement is preserved and not demoted by Stripe events.
  - `markPaymentSucceeded(...)` updates payment timestamps.
  - `markPaymentFailed(...)` marks non-family users as `past_due`.
  - `active_canceling` is treated as an access-granting status.

  ### `StripeWebhookEventDao`

  `StripeWebhookEventDao` manages webhook idempotency/audit.

  Important behaviors:

  - `createReceivedIfAbsent(...)` inserts a webhook event only once.
  - Duplicate Stripe event ids are ignored.
  - Events are later marked `processed`, `ignored`, or `failed`.

  ## Authentication and Authorization Filters

  The Stripe webhook endpoint must bypass both authentication and tenant authorization.

  Both filters were updated to treat these paths as public:

  ```text
  billing/stripe/webhook
  api/billing/stripe/webhook
  ```

  This was necessary because `AuthenticationFilter` could auto-authorize the webhook, but `TenantAuthorizationFilter` would still attempt to read a `CurrentUser` and return `401`.

  ## Subscription Enforcement

  A `SubscriptionAuthorizationFilter` was added.

  It runs after authentication and tenant authorization.

  It enforces local subscription access when:

  ```yaml
  billing:
    enforceSubscriptions: true
  ```

  When disabled, no subscription enforcement occurs.

  When enabled, users must have an access-granting local status.

  Access-granting statuses currently include:

  ```text
  active
  active_canceling
  trialing
  family
  ```

  `past_due` may also allow access until `current_period_end`, depending on the `UserSubscription.hasAccess(...)` logic.

  Billing endpoints remain reachable even when a user is not subscribed:

  ```text
  /api/billing/status
  /api/billing/checkout
  /api/billing/portal
  /api/billing/stripe/webhook
  ```

  This allows unsubscribed users to subscribe or manage billing.

  When access is blocked, the backend returns:

  ```json
  {
    "error": "subscription_required",
    "message": "An active NovelKMS subscription is required.",
    "status": "none"
  }
  ```

  with HTTP status:

  ```text
  402 Payment Required
  ```

  ## Frontend Components

  ### Billing API Wrapper

  Added:

  ```text
  novelkms-frontend/src/api/billing.js
  ```

  It wraps:

  ```text
  GET  /billing/status
  POST /billing/checkout
  POST /billing/portal
  ```

  The shared Axios client already uses:

  ```js
  baseURL: '/api'
  ```

  so frontend wrapper paths should not include `/api`.

  ### Billing Hook

  Added:

  ```text
  novelkms-frontend/src/hooks/useBilling.js
  ```

  Provides:

  ```js
  useBillingStatus()
  useCheckout()
  useBillingPortal()
  ```

  Checkout and portal mutations redirect the browser when the backend returns a Stripe-hosted URL.

  ### Billing Panel

  Added:

  ```text
  novelkms-frontend/src/components/subscription/BillingPanel.jsx
  ```

  Displays:

  - Current subscription status.
  - Active subscription messaging.
  - Trial messaging.
  - Complimentary/family access messaging.
  - Past-due messaging.
  - Scheduled cancellation messaging.
  - Subscribe button.
  - Manage billing button.
  - Refresh button.

  Mounted as a new `Billing` tab in:

  ```text
  novelkms-frontend/src/components/settings/SettingsDialog.jsx
  ```

  ### Stripe Return Pages

  Added:

  ```text
  novelkms-frontend/src/components/subscription/BillingReturnPage.jsx
  ```

  Handled frontend routes:

  ```text
  /billing/success
  /billing/cancel
  ```

  These are browser-facing SPA routes, not API routes.

  The success page checks billing status and offers:

  - Continue to NovelKMS.
  - Refresh status.

  The cancel page explains that no subscription changes were made.

  ### Global 402 Handling

  The shared frontend Axios client dispatches a browser event when the backend returns:

  ```text
  402 subscription_required
  ```

  The app listens for this event and opens:

  ```text
  Settings → Billing
  ```

  with a warning message.

  This prevents unsubscribed users from seeing generic API errors.

  ## Tested Scenarios

  The following scenarios have been tested successfully:

  - Stripe Checkout initiated from NovelKMS.
  - Stripe webhook receives and validates events.
  - `checkout.session.completed` activates the local user.
  - Subscription enforcement permits subscribed users.
  - Subscription enforcement blocks unsubscribed users.
  - Billing tab displays current status.
  - Stripe success/cancel return pages render correctly.
  - Global `402 subscription_required` opens Billing settings.
  - Stripe Customer Portal opens from NovelKMS.
  - User cancellation in Stripe Portal sends webhook events.
  - Active scheduled cancellation is stored as `active_canceling`.
  - Cancellation date is persisted.
  - Cancellation feedback/comment/reason are persisted.
  - `active_canceling` users retain access.

  ## Operational Notes

  When testing webhooks, Stripe may send events out of the expected order. For example:

  ```text
  invoice.payment_succeeded
  ```

  may arrive before the local subscription row exists. In that case, NovelKMS records the event but cannot map it to a local subscription.

  The key event for initial mapping is usually:

  ```text
  checkout.session.completed
  ```

  because it includes `client_reference_id`.

  Also, Stripe event retries with the same event id will not reprocess after NovelKMS has stored the event in `stripe_webhook_event`. To test changed webhook logic, generate a fresh Stripe event rather than resending an already-processed event.

  ## Useful SQL Checks

  Inspect local subscriptions:

  ```sql
  SELECT
      user_id,
      status,
      stripe_customer_id,
      stripe_subscription_id,
      cancel_at_period_end,
      cancel_at,
      current_period_start,
      current_period_end,
      cancellation_feedback,
      cancellation_comment,
      cancellation_reason,
      canceled_at,
      updated_at
  FROM user_subscription;
  ```

  Inspect recent webhook events:

  ```sql
  SELECT
      event_type,
      processing_status,
      related_user_id,
      related_customer_id,
      related_subscription_id,
      error_message,
      received_at,
      processed_at
  FROM stripe_webhook_event
  ORDER BY received_at DESC
  LIMIT 20;
  ```

  Inspect only subscription update events:

  ```sql
  SELECT
      event_type,
      processing_status,
      related_user_id,
      related_customer_id,
      related_subscription_id,
      error_message,
      received_at,
      processed_at
  FROM stripe_webhook_event
  WHERE event_type = 'customer.subscription.updated'
  ORDER BY received_at DESC
  LIMIT 20;
  ```

  ## Next Steps

  Recommended next steps:

  - Add an admin-only way to grant, inspect, and revoke `family` access.
  - Add an admin billing/status view for support troubleshooting.
  - Add plan mapping so `stripe_price_id` resolves to a friendly internal `plan_key`.
  - Add clearer frontend messaging for `active_canceling`, including the final access date.
  - Decide whether `past_due` should allow a grace period and for how long.
  - Add tests for `UserSubscription.hasAccess(...)`.
  - Add tests for `StripeWebhookResource` subscription parsing, especially `cancel_at`, `cancellation_details`, and item-level period fields.
  - Add a safe webhook replay/dev diagnostic tool or script for local testing.
  - Consider storing selected raw webhook payloads in development or debug mode only, but avoid storing full payloads in production unless privacy/security review is completed.
  - Add production monitoring/alerting for failed webhook events.
  - Add a scheduled reconciliation job that periodically compares local subscription state with Stripe for active customers.
  - Finalize live-mode Stripe configuration and repeat all tests with live keys before accepting real users.
