-- V3 — Page layout settings (H2 dialect)
-- H2 requires a separate ALTER TABLE statement per column.

ALTER TABLE project ADD COLUMN author_first_name VARCHAR(100);
ALTER TABLE project ADD COLUMN author_last_name  VARCHAR(100);

ALTER TABLE book ADD COLUMN page_layout_enabled   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE book ADD COLUMN page_size_preset      VARCHAR(20) NOT NULL DEFAULT 'LETTER';
ALTER TABLE book ADD COLUMN page_width_in         DECIMAL(6,3);
ALTER TABLE book ADD COLUMN page_height_in        DECIMAL(6,3);
ALTER TABLE book ADD COLUMN page_margin_top_in    DECIMAL(4,3) NOT NULL DEFAULT 1.000;
ALTER TABLE book ADD COLUMN page_margin_bottom_in DECIMAL(4,3) NOT NULL DEFAULT 1.000;
ALTER TABLE book ADD COLUMN page_margin_inner_in  DECIMAL(4,3) NOT NULL DEFAULT 1.250;
ALTER TABLE book ADD COLUMN page_margin_outer_in  DECIMAL(4,3) NOT NULL DEFAULT 1.000;