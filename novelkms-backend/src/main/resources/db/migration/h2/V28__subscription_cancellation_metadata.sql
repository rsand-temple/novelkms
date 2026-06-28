ALTER TABLE user_subscription
    ADD COLUMN cancel_at TIMESTAMP;

ALTER TABLE user_subscription
    ADD COLUMN cancellation_feedback VARCHAR(255);

ALTER TABLE user_subscription
    ADD COLUMN cancellation_comment CLOB;

ALTER TABLE user_subscription
    ADD COLUMN cancellation_reason VARCHAR(255);

ALTER TABLE user_subscription
    DROP CONSTRAINT IF EXISTS ck_user_subscription_status;

ALTER TABLE user_subscription
    ADD CONSTRAINT ck_user_subscription_status
    CHECK (status IN (
        'none',
        'incomplete',
        'incomplete_expired',
        'trialing',
        'active',
        'active_canceling',
        'past_due',
        'canceled',
        'unpaid',
        'paused',
        'family'
    ));

CREATE INDEX IF NOT EXISTS ix_user_subscription_cancel_at
    ON user_subscription(cancel_at);