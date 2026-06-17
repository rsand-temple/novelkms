CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email_address VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(200) NOT NULL,
    mobile_number VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);
CREATE TABLE user_identity (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL, provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320), provider_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_login_at TIMESTAMP,
    CONSTRAINT uq_user_identity_provider_subject UNIQUE(provider, provider_subject)
);
CREATE TABLE user_session (
    token_hash CHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, ip_address VARCHAR(64), user_agent VARCHAR(500), revoked_at TIMESTAMP
);
CREATE INDEX ix_user_session_user ON user_session(user_id);
CREATE INDEX ix_user_session_expiry ON user_session(expires_at);
CREATE TABLE oauth_state (
    state_hash CHAR(64) PRIMARY KEY, provider VARCHAR(20) NOT NULL, return_path VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE pending_registration (
    id UUID PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL, email_address VARCHAR(320) NOT NULL, email_verified BOOLEAN NOT NULL,
    suggested_first_name VARCHAR(100), suggested_last_name VARCHAR(100), expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pending_identity UNIQUE(provider, provider_subject)
);
