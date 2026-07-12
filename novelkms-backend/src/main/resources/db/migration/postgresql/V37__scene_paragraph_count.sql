-- ===========================================================================
-- V37 — Paragraph count for the page-count estimate
--
-- See the h2/ dialect copy for the full rationale. Adds `paragraph_count`
-- alongside the existing `word_count` on `scene`, derived server-side by
-- SceneDao from the saved HTML on every content write.
-- ===========================================================================

ALTER TABLE scene ADD COLUMN paragraph_count INTEGER NOT NULL DEFAULT 0;
