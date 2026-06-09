-- =============================================================================
-- V3 — Page layout settings
-- Place this file in BOTH:
--   src/main/resources/db/migration/h2/V3__page_layout.sql
--   src/main/resources/db/migration/postgresql/V3__page_layout.sql
-- The SQL is identical for both dialects.
-- =============================================================================

-- Author fields live on the project (one author per project / series).
ALTER TABLE project
    ADD COLUMN author_first_name VARCHAR(100),
    ADD COLUMN author_last_name  VARCHAR(100);

-- Page layout settings are per-book (different books in a series may be
-- different sizes, e.g. a trade paperback volume vs. a hardback omnibus).
ALTER TABLE book
    ADD COLUMN page_layout_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN page_size_preset       VARCHAR(20)  NOT NULL DEFAULT 'LETTER',
    ADD COLUMN page_width_in          DECIMAL(6,3),          -- NULL for non-CUSTOM presets
    ADD COLUMN page_height_in         DECIMAL(6,3),          -- NULL for non-CUSTOM presets
    ADD COLUMN page_margin_top_in     DECIMAL(4,3) NOT NULL DEFAULT 1.000,
    ADD COLUMN page_margin_bottom_in  DECIMAL(4,3) NOT NULL DEFAULT 1.000,
    ADD COLUMN page_margin_inner_in   DECIMAL(4,3) NOT NULL DEFAULT 1.250,  -- spine side
    ADD COLUMN page_margin_outer_in   DECIMAL(4,3) NOT NULL DEFAULT 1.000;  -- edge side
