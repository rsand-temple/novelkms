-- =============================================================================
-- NovelKMS  V7 — Book import metadata  (H2)
-- =============================================================================
--
-- Tracks the origin of imported books. Both columns are nullable — they are
-- only populated by the import pipeline and are never touched by normal edits.
--
-- imported_from : original filename (e.g. "TheAloneMan.docx")
-- imported_at   : UTC timestamp of the import run
--
-- H2 requires one ALTER TABLE statement per column.
-- =============================================================================

ALTER TABLE book ADD COLUMN imported_from VARCHAR(500);
ALTER TABLE book ADD COLUMN imported_at   TIMESTAMP;
