-- ===========================================================================
-- V37 — Paragraph count for the page-count estimate
--
-- Adds `paragraph_count` alongside the existing `word_count` on `scene`.
-- Unlike word_count (computed client-side by TipTap and sent on save),
-- paragraph_count is derived server-side by SceneDao from the saved HTML on
-- every content write (see SceneDao.saveContent / recalculateAllWordCounts),
-- so no frontend save-path changes are needed and every write path (manual
-- edit, DOCX import, codex AI fill promotion, starter content) picks it up
-- automatically.
--
-- Summed the same way word_count already is by BookDao/PartDao/ProjectDao, to
-- feed the editor status bar's estimated page count (words + paragraphs +
-- page size — see utils/pageEstimate.js on the frontend).
-- ===========================================================================

ALTER TABLE scene ADD COLUMN paragraph_count INTEGER NOT NULL DEFAULT 0;
