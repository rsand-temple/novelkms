-- =============================================================================
-- NovelKMS  V2 - Add subtitle fields
-- =============================================================================
-- Identical for both h2/ and postgresql/ migration directories.
-- Place this file in BOTH:
--   src/main/resources/db/migration/h2/V2__add_subtitle_fields.sql
--   src/main/resources/db/migration/postgresql/V2__add_subtitle_fields.sql
-- Adjust the version number if other migrations exist between V1 and this one.

ALTER TABLE book    ADD COLUMN short_title VARCHAR(255);
ALTER TABLE part    ADD COLUMN subtitle    VARCHAR(255);
ALTER TABLE chapter ADD COLUMN subtitle    VARCHAR(255);
