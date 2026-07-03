-- ===========================================================================
-- V35 — AI prompt template overrides (chapter summary / book summary / editorial)
--
-- Adds the same single-block selection mechanism already used by memory-document
-- templates (V24) to the three remaining free-text generation paths: chapter
-- summary, book summary, and editorial. Resolution for each type is:
--
--     book override  ->  project override  ->  user global  ->  system default
--
-- System defaults are Java constants (ChapterSummaryTemplateDefaults,
-- BookSummaryTemplateDefaults, EditorialTemplateDefaults) with no DB row, and
-- are therefore uneditable by construction — mirroring AiFormInstructionsDefaults
-- and ChapterMemoryTemplateDefaults.
--
-- Three nullable columns are added to project and book — one per template type.
-- NULL means "no override at this scope." Three per-user global tables (one per
-- type) mirror the memory_template_global pattern from V24.
--
-- One ALTER TABLE per column, per the H2 rule. All DDL is valid in both
-- H2 (MODE=PostgreSQL) and PostgreSQL, so this file is identical across the two
-- dialects.
-- ===========================================================================

-- Optional project-level overrides (NULL = no override at this scope).
ALTER TABLE project ADD COLUMN chapter_summary_template TEXT;
ALTER TABLE project ADD COLUMN book_summary_template     TEXT;
ALTER TABLE project ADD COLUMN editorial_template        TEXT;

-- Optional book-level overrides (NULL = no override at this scope).
ALTER TABLE book ADD COLUMN chapter_summary_template TEXT;
ALTER TABLE book ADD COLUMN book_summary_template     TEXT;
ALTER TABLE book ADD COLUMN editorial_template        TEXT;

-- User-global chapter-summary template. Absent row = fall back to system default.
CREATE TABLE chapter_summary_template_global (
    user_id    UUID      NOT NULL PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User-global book-summary template. Absent row = fall back to system default.
CREATE TABLE book_summary_template_global (
    user_id    UUID      NOT NULL PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User-global editorial template. Absent row = fall back to system default.
CREATE TABLE editorial_template_global (
    user_id    UUID      NOT NULL PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
