-- ===========================================================================
-- V26 — One-time author guidance for AI generation
--
-- Adds an optional, free-text "guidance" note the author can supply at
-- generation time for any of the four AI artifact families: chapter/scene
-- review, chapter memory document, chapter summary, and book summary. It is
-- NOT a persistent override like ai_form_instructions (V20) or
-- memory_template (V24) — there is no scope cascade and no separate table.
-- It is purely an addendum to the prompt for that one generation call,
-- recorded on the resulting artifact as immutable provenance (mirroring how
-- ai_review already stamps form_scope/form_instructions), and exposed back
-- to the frontend so each generation surface can pre-fill its guidance field
-- with whatever was used last time, letting the author tweak and re-run
-- without retyping.
--
-- Explicitly out of scope here: feeding selected Codex/canon entries into the
-- prompt (the dormant ReviewRequest.referenceContext path) — that remains a
-- separate, later increment.
--
-- One ALTER TABLE per column, per the H2 rule. Identical in both dialects.
-- ===========================================================================

ALTER TABLE ai_review       ADD COLUMN user_guidance TEXT;
ALTER TABLE chapter_memory  ADD COLUMN user_guidance TEXT;
ALTER TABLE chapter_summary ADD COLUMN user_guidance TEXT;
ALTER TABLE book_summary    ADD COLUMN user_guidance TEXT;
