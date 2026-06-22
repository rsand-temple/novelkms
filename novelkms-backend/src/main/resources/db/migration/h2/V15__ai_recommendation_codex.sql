-- ===========================================================================
-- V15 — AI review recommendations: codex promotion support
--
-- Each recommendation now carries the model's suggested KMS filing target
-- (codex_category, one of the codex_category keys) and a concise codex_title,
-- so promoting a recommendation into the Codex is a one-click operation.
-- promoted_scene_id records the entry created on promotion (nullable until
-- promoted) — it lets the UI show an "Added" state and prevents a second click
-- from creating a duplicate entry.
--
-- Separate ADD COLUMN statements (valid in both H2 and PostgreSQL); this file
-- is identical to the postgresql dialect file.
-- ===========================================================================

ALTER TABLE ai_review_recommendation ADD COLUMN codex_category    VARCHAR(20);
ALTER TABLE ai_review_recommendation ADD COLUMN codex_title       VARCHAR(255);
ALTER TABLE ai_review_recommendation ADD COLUMN promoted_scene_id UUID;
