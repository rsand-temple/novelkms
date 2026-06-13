-- =============================================================================
-- NovelKMS  V7 — Book import metadata  (PostgreSQL)
-- =============================================================================
--
-- Tracks the origin of imported books. Both columns are nullable — they are
-- only populated by the import pipeline and are never touched by normal edits.
--
-- imported_from : original filename (e.g. "TheAloneMan.docx")
-- imported_at   : UTC timestamp of the import run
-- =============================================================================

ALTER TABLE book
    ADD COLUMN imported_from VARCHAR(500),
    ADD COLUMN imported_at   TIMESTAMP;
