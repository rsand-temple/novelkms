-- ===========================================================================
-- V22 — Page layout becomes a scoped bundle (BOOK -> PROJECT -> SYSTEM)
--
-- Page layout was 8 physical columns on `book`. Path A makes it a scoped
-- override bundle like editor_settings/ai-form: one lazily-seeded SYSTEM default
-- (PageLayoutDefaults), optional PROJECT and BOOK overrides. Resolution for a
-- book is BOOK -> PROJECT -> SYSTEM. Page layout affects export/preview only,
-- never the live editor.
--
-- Typed columns (not a JSON definition) are used here because page layout has a
-- small fixed schema and the export path wants typed values — and it makes the
-- data copy below a clean column-to-column INSERT ... SELECT.
--
-- Clean break: existing per-book values are copied into BOOK rows (only where
-- they differ from the factory default, so unconfigured books simply inherit
-- SYSTEM and behavior is preserved), then the 8 book columns are dropped so the
-- bundle is the single source of truth. One DROP per column (H2 rule).
--
-- This file differs from its sibling dialect ONLY in the row-id function used by
-- the data copy (H2 RANDOM_UUID() vs PostgreSQL gen_random_uuid()), as V19
-- already establishes dialect-specific files are acceptable.
-- ===========================================================================

CREATE TABLE page_layout (
    id                     UUID         NOT NULL PRIMARY KEY,
    scope                  VARCHAR(20)  NOT NULL,                                  -- SYSTEM | PROJECT | BOOK
    project_id             UUID                  REFERENCES project(id) ON DELETE CASCADE,
    book_id                UUID                  REFERENCES book(id)    ON DELETE CASCADE,
    page_layout_enabled    BOOLEAN      NOT NULL,
    page_size_preset       VARCHAR(40)  NOT NULL,
    page_width_in          DECIMAL(6,3),
    page_height_in         DECIMAL(6,3),
    page_margin_top_in     DECIMAL(4,3) NOT NULL,
    page_margin_bottom_in  DECIMAL(4,3) NOT NULL,
    page_margin_inner_in   DECIMAL(4,3) NOT NULL,
    page_margin_outer_in   DECIMAL(4,3) NOT NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One PROJECT row per project; one BOOK row per book. SYSTEM row has both ids
-- NULL (multiple NULLs permitted), uniqueness enforced by the DAO find-or-create.
CREATE UNIQUE INDEX uq_page_layout_project ON page_layout(project_id);
CREATE UNIQUE INDEX uq_page_layout_book    ON page_layout(book_id);

-- Copy each customized book's layout into a BOOK override row.
INSERT INTO page_layout (id, scope, book_id, page_layout_enabled, page_size_preset,
        page_width_in, page_height_in, page_margin_top_in, page_margin_bottom_in,
        page_margin_inner_in, page_margin_outer_in, created_at, updated_at)
SELECT RANDOM_UUID(), 'BOOK', b.id, b.page_layout_enabled, b.page_size_preset,
       b.page_width_in, b.page_height_in, b.page_margin_top_in, b.page_margin_bottom_in,
       b.page_margin_inner_in, b.page_margin_outer_in, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM book b
WHERE b.page_layout_enabled = TRUE
   OR b.page_size_preset <> 'LETTER'
   OR b.page_width_in IS NOT NULL
   OR b.page_height_in IS NOT NULL
   OR b.page_margin_top_in    <> 1.000
   OR b.page_margin_bottom_in <> 1.000
   OR b.page_margin_inner_in  <> 1.250
   OR b.page_margin_outer_in  <> 1.000;

-- Drop the per-book columns (single source of truth is now page_layout).
ALTER TABLE book DROP COLUMN page_layout_enabled;
ALTER TABLE book DROP COLUMN page_size_preset;
ALTER TABLE book DROP COLUMN page_width_in;
ALTER TABLE book DROP COLUMN page_height_in;
ALTER TABLE book DROP COLUMN page_margin_top_in;
ALTER TABLE book DROP COLUMN page_margin_bottom_in;
ALTER TABLE book DROP COLUMN page_margin_inner_in;
ALTER TABLE book DROP COLUMN page_margin_outer_in;
