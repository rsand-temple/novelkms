-- ===========================================================================
-- V14 — AI Review framework
--
-- Adds per-user AI provider credentials and the chapter-review artifact model.
-- All three tables are scoped to app_user (ON DELETE CASCADE) so AI settings
-- and review history live in "user settings", consistent with the auth model
-- introduced in V9 and the project ownership added in V10.
--
-- Design notes:
--   * ai_credential supports multiple providers AND multiple keys per provider
--     (distinguished by label). Exactly one credential per user is flagged
--     is_default. The API key is stored ENCRYPTED (AES-GCM) in
--     api_key_encrypted; key_last4 holds a non-secret hint for display.
--   * ai_review is an immutable artifact. Re-running a review creates a new
--     row. status moves PENDING -> COMPLETED | FAILED. submitted_at /
--     completed_at are retained even though v1 runs synchronously, so an
--     async/polling execution model can be added later with no schema change.
--   * ai_review_recommendation rows carry a per-review seq (1-based) and a
--     lifecycle status OPEN -> ACCEPTED | REJECTED.
--
-- TEXT and BOOLEAN ... DEFAULT FALSE are valid in both H2 (MODE=PostgreSQL)
-- and PostgreSQL, so this file is identical to the postgresql dialect file.
-- ===========================================================================

CREATE TABLE ai_credential (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider          VARCHAR(40)  NOT NULL,
    label             VARCHAR(100) NOT NULL,
    api_key_encrypted TEXT         NOT NULL,
    key_last4         VARCHAR(8),
    default_model     VARCHAR(100),
    is_default        BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_ai_credential_user_provider_label ON ai_credential(user_id, provider, label);
CREATE INDEX ix_ai_credential_user ON ai_credential(user_id);

CREATE TABLE ai_review (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    project_id     UUID,
    book_id        UUID,
    chapter_id     UUID NOT NULL,
    provider       VARCHAR(40)  NOT NULL,
    model          VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    submitted_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at   TIMESTAMP,
    prompt_version VARCHAR(40),
    error_message  VARCHAR(2000),
    response_json  TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_ai_review_chapter ON ai_review(chapter_id);
CREATE INDEX ix_ai_review_user ON ai_review(user_id);

CREATE TABLE ai_review_recommendation (
    id             UUID PRIMARY KEY,
    review_id      UUID NOT NULL REFERENCES ai_review(id) ON DELETE CASCADE,
    seq            INT  NOT NULL,
    category       VARCHAR(60),
    severity       VARCHAR(20),
    location       VARCHAR(500),
    recommendation TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_ai_review_rec_review ON ai_review_recommendation(review_id);
