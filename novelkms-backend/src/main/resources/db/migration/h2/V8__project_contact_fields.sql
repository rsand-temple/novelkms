-- =============================================================================
-- NovelKMS  V8 — Project contact fields  (H2)
-- =============================================================================
-- Adds display name, email address, and phone number to the project table.
-- All three are surfaced as template tokens (DISPLAY_NAME, EMAIL, PHONE) on
-- cover and other page templates, and appear in the project properties panel.
-- H2 requires one ALTER TABLE statement per column.
-- =============================================================================

ALTER TABLE project ADD COLUMN display_name  VARCHAR(200);
ALTER TABLE project ADD COLUMN email_address VARCHAR(200);
ALTER TABLE project ADD COLUMN phone_number  VARCHAR(50);
