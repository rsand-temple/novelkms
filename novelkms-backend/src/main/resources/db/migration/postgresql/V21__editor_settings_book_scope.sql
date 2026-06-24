-- ===========================================================================
-- V21 — Document ("editor") settings gain a BOOK scope
--
-- editor_settings previously cascaded SYSTEM -> USER -> PROJECT (V17). Path A of
-- the settings restructure makes the scope model uniform across document, page
-- layout, and AI settings, so a book in a series can carry its own typography.
--
-- New resolution for a book: BOOK -> PROJECT -> USER -> SYSTEM. BOOK rows are
-- copy-on-write overrides (one per book), exactly like the PROJECT rows: present
-- means "this book overrides," absent means "inherit." This drives both the live
-- editor render and export for the open book.
--
-- The FK is added as a separate named constraint rather than inline on ADD
-- COLUMN, since inline column-level REFERENCES in ALTER ... ADD COLUMN is not
-- uniformly supported; ALTER ... ADD CONSTRAINT ... ON DELETE CASCADE is valid
-- in both H2 (MODE=PostgreSQL) and PostgreSQL, so this file is identical across
-- dialects (as with V14/V17/V20). One column added; NULLs are distinct in the
-- UNIQUE index, matching the existing project_id index.
-- ===========================================================================

ALTER TABLE editor_settings ADD COLUMN book_id UUID;

ALTER TABLE editor_settings
    ADD CONSTRAINT fk_editor_settings_book
    FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE;

-- One BOOK row per book (BOOK rows leave user_id/project_id NULL).
CREATE UNIQUE INDEX uq_editor_settings_book ON editor_settings(book_id);
