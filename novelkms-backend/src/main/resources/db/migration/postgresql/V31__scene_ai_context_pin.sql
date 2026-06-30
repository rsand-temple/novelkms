-- ===========================================================================
-- V30 — Codex entries shared as AI "project files" (pinned reference context)
--
-- Lets the author selectively share individual Codex entries with the AI as
-- reference material for chapter/scene reviews, without dumping the entire
-- Codex at the model. A pinned entry is fed into the review prompt's existing
-- (previously always-null) referenceContext block as "established canon/voice
-- the manuscript must respect — do not review it."
--
-- A Codex entry is a scene row whose parent chapter is a codex category
-- (chapter.codex_id set, chapter.book_id NULL). The flag lives on scene and is
-- inert for manuscript scenes — exactly the same modeling precedent as
-- chapter.resets_numbering, which is likewise meaningful only for some rows.
-- Default FALSE means nothing is shared until the author opts an entry in, so
-- existing projects are unaffected.
--
-- One ALTER TABLE per column, per the H2 rule. Identical in both dialects.
-- ===========================================================================

ALTER TABLE scene ADD COLUMN ai_context_pinned BOOLEAN NOT NULL DEFAULT FALSE;
