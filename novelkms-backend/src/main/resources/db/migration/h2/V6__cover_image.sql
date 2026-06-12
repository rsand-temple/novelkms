-- =============================================================================
-- NovelKMS  V6 — Book cover image  (H2)
-- =============================================================================
--
-- Stores the cover image as a BLOB directly in the book row. This keeps the
-- project fully self-contained (no file-system paths to manage) and works with
-- both the H2 dev database and a future PostgreSQL deployment.
--
-- cover_image          — raw binary; NULL when no image has been uploaded.
-- cover_image_mime_type — e.g. 'image/jpeg', 'image/png'; NULL when no image.
--
-- The image bytes are never included in the standard book SELECT — callers use
-- the dedicated GET /api/books/{id}/cover-image endpoint.  The boolean flag
-- has_cover_image is derived at query time via a CASE expression so callers
-- know whether an image exists without loading the bytes.
--
-- H2 dialect: one ALTER TABLE per column (H2 does not support adding multiple
-- columns in a single ALTER TABLE statement).
-- =============================================================================

ALTER TABLE book ADD COLUMN cover_image           BINARY LARGE OBJECT;
ALTER TABLE book ADD COLUMN cover_image_mime_type VARCHAR(50);