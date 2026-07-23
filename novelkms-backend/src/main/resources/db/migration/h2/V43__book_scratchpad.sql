-- =============================================================================
-- NovelKMS  V43 - Book Scratchpad  (H2)
-- =============================================================================
-- A Scratchpad is a per-book holding pen for scenes that are not (yet) part of
-- the manuscript. It reuses the chapter/scene tables the same way the Codex
-- does: the Scratchpad is a chapter row whose book_id, part_id and codex_id are
-- all NULL, and whose scratchpad_book_id names the book it belongs to.
--
-- That NULL book_id is the whole mechanism. Every book-rooted query in the
-- application filters on chapter.book_id -- the numbering CTE, BookOutline,
-- bookChapterSummaries, ChapterMemoryDao, SceneDao.findContentForBook, the word
-- and paragraph rollups in BookDao/ProjectDao/PartDao, the trash child counts,
-- and all three export services. A Scratchpad chapter therefore drops out of
-- every one of them with no filtering added anywhere, exactly as codex category
-- chapters have since V13. It also fails safe: a book-rooted query written in
-- future is excluded by default rather than having to remember a new guard.
--
-- Scenes inside the Scratchpad are ordinary scene rows. They are editable,
-- draggable into and out of the manuscript, and individually trashable.
--
-- One Scratchpad per book, created lazily on first read (there is no backfill
-- here: existing books get a row the first time the Scratchpad is opened). The
-- unique index enforces the "one" -- NULLs are distinct in a unique index in
-- both H2 and PostgreSQL, so the many chapter rows that are not Scratchpads do
-- not collide, the same trick uq_codex_project relies on. A Scratchpad chapter
-- is never soft-deleted, so no filtered/partial index is needed.
--
-- All types used here are valid in both H2 and PostgreSQL, so this file is
-- identical to the postgresql dialect file.
-- =============================================================================

ALTER TABLE chapter ADD COLUMN scratchpad_book_id UUID;

ALTER TABLE chapter ADD CONSTRAINT fk_chapter_scratchpad_book
    FOREIGN KEY (scratchpad_book_id) REFERENCES book(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_chapter_scratchpad ON chapter(scratchpad_book_id);
