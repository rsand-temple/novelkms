-- ===========================================================================
-- V19 — AI review scope (chapter / scene)
--
-- A chapter review and a scene review are the same kind of artifact; they
-- differ only in scope. scope is DERIVED, not stored:
--
--     chapter_id IS NULL  -> BOOK    (reserved for a future book-scope review)
--     scene_id   IS NOT NULL -> SCENE
--     otherwise              -> CHAPTER
--
-- A scene review records its parent chapter in chapter_id (so it groups under
-- that chapter's AI workflow via the existing ix_ai_review_chapter listing)
-- and the reviewed scene in the new scene_id column.
--
-- chapter_id is relaxed to NULLABLE here purely as forward preparation for a
-- book-scope review (chapter_id blank == book scope). No code path inserts a
-- null chapter_id yet; every chapter and scene review still sets it.
--
-- H2 (MODE=PostgreSQL) drops a NOT NULL constraint with
-- "ALTER COLUMN ... SET NULL" — see the postgresql dialect file for the
-- equivalent "DROP NOT NULL".
-- ===========================================================================

ALTER TABLE ai_review ADD COLUMN scene_id UUID;
CREATE INDEX ix_ai_review_scene ON ai_review(scene_id);

ALTER TABLE ai_review ALTER COLUMN chapter_id SET NULL;
