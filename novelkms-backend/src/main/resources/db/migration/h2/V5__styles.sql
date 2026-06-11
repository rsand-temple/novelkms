-- =============================================================================
-- NovelKMS  V5 — Paragraph styles (cascading global / project / book)  (H2)
-- =============================================================================
--
-- A style is a named bundle of paragraph formatting (font, size, bold/italic,
-- indents, spacing). The roster of style keys is fixed in code
-- (StyleDefaults.STYLE_KEYS); only the definitions are stored/editable.
--
--   style_key  : e.g. 'normal', 'h1', 'blockquote', 'report', 'chapter_title'
--   scope      : 'GLOBAL'  — the editable default spanning all projects
--              | 'PROJECT' — a per-project override
--              | 'BOOK'    — a per-book override
--   project_id : set for PROJECT rows; NULL otherwise
--   book_id    : set for BOOK rows; NULL otherwise
--   definition : JSON — { fontFamily, fontSize, bold, italic,
--                         firstLineIndent, textIndent, spacingBefore, spacingAfter }
--
-- Resolution for a (project, book, key): BOOK → PROJECT → GLOBAL.
-- The GLOBAL row per key is lazily seeded from a code constant on first fetch
-- (StyleDao.getOrCreateGlobal).
-- =============================================================================

CREATE TABLE style (
    id          UUID         NOT NULL PRIMARY KEY,
    style_key   VARCHAR(50)  NOT NULL,
    scope       VARCHAR(20)  NOT NULL,
    project_id  UUID                  REFERENCES project(id) ON DELETE CASCADE,
    book_id     UUID                  REFERENCES book(id)    ON DELETE CASCADE,
    definition  TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_style_lookup ON style(scope, style_key);

-- One override row per (project, key) and per (book, key). GLOBAL rows have both
-- ids NULL; multiple NULLs are permitted, so global uniqueness is enforced by
-- the DAO's find-or-create logic.
CREATE UNIQUE INDEX uq_style_project_key ON style(project_id, style_key);
CREATE UNIQUE INDEX uq_style_book_key    ON style(book_id, style_key);
