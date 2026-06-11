-- =============================================================================
-- NovelKMS  V4 — Page templates (cover / part) + project copyright  (H2)
-- H2 requires a separate ALTER TABLE statement per column.
-- =============================================================================

-- Copyright text lives on the project (one author / copyright line per series).
-- Resolved by the COPYRIGHT template token; also intended for a future page
-- template / about page.
ALTER TABLE project ADD COLUMN copyright VARCHAR(500);

-- -----------------------------------------------------------------------------
-- Templates govern the layout of generated pages (cover page, part page, ...).
--
--   template_type : 'COVER' | 'PART'
--   scope         : 'GLOBAL' (the editable default, spanning all projects)
--                 | 'BOOK'   (a per-book override)
--   book_id       : NULL for GLOBAL rows; the owning book for BOOK rows.
--   content       : TipTap HTML, including <span data-token="..."> field tokens.
--
-- Resolution order for a given book + type: BOOK override -> GLOBAL default.
-- The single GLOBAL row per type is lazily seeded from a code constant on
-- first fetch (TemplateDao.getOrCreateGlobal).
-- -----------------------------------------------------------------------------

CREATE TABLE template (
    id            UUID         NOT NULL PRIMARY KEY,
    template_type VARCHAR(20)  NOT NULL,
    scope         VARCHAR(20)  NOT NULL,
    book_id       UUID                  REFERENCES book(id) ON DELETE CASCADE,
    content       TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_template_lookup ON template(template_type, scope, book_id);

-- One override row per (book, type). GLOBAL rows have book_id NULL; multiple
-- NULLs are permitted by a unique index, so global uniqueness is enforced by
-- the DAO's find-or-create logic instead.
CREATE UNIQUE INDEX uq_template_book_type ON template(book_id, template_type);
