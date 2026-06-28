CREATE TABLE user_subscription (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,

    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),

    status VARCHAR(50) NOT NULL DEFAULT 'none',
    plan_key VARCHAR(100),
    stripe_price_id VARCHAR(255),
    stripe_product_id VARCHAR(255),

    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    trial_start TIMESTAMP,
    trial_end TIMESTAMP,

    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    canceled_at TIMESTAMP,

    last_payment_succeeded_at TIMESTAMP,
    last_payment_failed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_user_subscription_status
        CHECK (status IN (
            'none',
            'incomplete',
            'incomplete_expired',
            'trialing',
            'active',
            'past_due',
            'canceled',
            'unpaid',
            'paused',
            'family'
        ))
);

CREATE UNIQUE INDEX ux_user_subscription_stripe_customer
    ON user_subscription(stripe_customer_id);

CREATE UNIQUE INDEX ux_user_subscription_stripe_subscription
    ON user_subscription(stripe_subscription_id);

CREATE INDEX ix_user_subscription_status
    ON user_subscription(status);

CREATE INDEX ix_user_subscription_current_period_end
    ON user_subscription(current_period_end);


CREATE TABLE stripe_webhook_event (
    stripe_event_id VARCHAR(255) PRIMARY KEY,

    event_type VARCHAR(255) NOT NULL,
    livemode BOOLEAN NOT NULL DEFAULT FALSE,

    stripe_created_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'received',
    error_message CLOB,

    related_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    related_customer_id VARCHAR(255),
    related_subscription_id VARCHAR(255),

    CONSTRAINT ck_stripe_webhook_event_status
        CHECK (processing_status IN (
            'received',
            'processed',
            'ignored',
            'failed'
        ))
);

CREATE INDEX ix_stripe_webhook_event_type
    ON stripe_webhook_event(event_type);

CREATE INDEX ix_stripe_webhook_event_received_at
    ON stripe_webhook_event(received_at);

CREATE INDEX ix_stripe_webhook_event_customer
    ON stripe_webhook_event(related_customer_id);

CREATE INDEX ix_stripe_webhook_event_subscription
    ON stripe_webhook_event(related_subscription_id);

CREATE INDEX ix_stripe_webhook_event_user
    ON stripe_webhook_event(related_user_id);