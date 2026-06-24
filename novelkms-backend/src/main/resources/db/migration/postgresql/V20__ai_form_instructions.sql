-- ===========================================================================
-- V20 — AI form instructions (editorial "form" vs constant "functional")
--
-- The AI review system prompt is split into two parts:
--
--   * FUNCTIONAL — the JSON output contract NovelKMS consumes (fields, enums,
--     anchorText rules, the response shape). This is CONSTANT and lives in
--     OpenAiProvider; it is never editable at any scope and has no storage.
--
--   * FORM — the editorial persona and behavioral constraints ("you are the
--     editor; do not rewrite the manuscript; do not introduce new characters").
--     This IS author-editable, at three independent scopes.
--
-- Resolution for a review is single-block SELECTION (no inheritance, no
-- concatenation), most-specific first:
--
--     book override  ->  project override  ->  user global  ->  system default
--
--   * system default — a non-editable Java constant (AiFormInstructionsDefaults),
--     so there is no DB row for it; it is uneditable by construction.
--   * user global     — ai_form_global, one row per user; the user's personal
--     editable layer over the system default.
--   * project override / book override — optional, standalone, nullable columns
--     on project / book. NULL means "no override"; nothing to backfill. They ride
--     the existing soft-delete on those tables for free.
--
-- Each ai_review records the form text that actually governed it (form_scope +
-- form_instructions), alongside response_json, so the immutable artifact stays
-- faithful even if the user later edits their global.
--
-- All DDL below (ADD COLUMN ... TEXT, CREATE TABLE, VARCHAR) is valid in both
-- H2 (MODE=PostgreSQL) and PostgreSQL, so this file is identical across the two
-- dialects (as with V14 and V17). One ALTER TABLE per column, per the H2 rule.
-- ===========================================================================

-- Optional standalone overrides (NULL = no override).
ALTER TABLE project ADD COLUMN ai_form_instructions TEXT;
ALTER TABLE book    ADD COLUMN ai_form_instructions TEXT;

-- The user's personal editable global. Absent row = fall back to system default.
CREATE TABLE ai_form_global (
    user_id      UUID      NOT NULL PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    instructions TEXT      NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Provenance: what governed this immutable review.
ALTER TABLE ai_review ADD COLUMN form_scope        VARCHAR(20);
ALTER TABLE ai_review ADD COLUMN form_instructions TEXT;
