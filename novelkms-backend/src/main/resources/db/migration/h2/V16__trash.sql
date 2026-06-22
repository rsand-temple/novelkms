-- ===========================================================================
-- V16 — Trash (soft delete)
--
-- Adds a per-user trash can. Deleting any trashable entity now soft-deletes it
-- (sets deleted_at) and records a trash_batch row; the row and its descendants
-- stay in place so a restore preserves stable IDs (which the AI round-trip and
-- future versioning depend on). Only the *root* that was deleted is stamped —
-- descendants are hidden transitively because every live read filters
-- deleted_at IS NULL and children are only reached through their parent.
--
-- Trashable roots: project, book, chapter (manuscript or codex category),
-- scene (manuscript or codex entry), ai_review. Part is intentionally NOT
-- trashable: deleting a part keeps its existing special behavior (promote its
-- chapters to direct-book children via ON DELETE SET NULL, then hard delete).
--
-- trash_batch is the per-user index the Trash UI renders from, so the listing
-- never scans five tables. root_id has no FK (it points into different tables
-- depending on root_type); dangling rows are removed by an orphan sweep after
-- any purge.
--
-- All types here (TIMESTAMP, UUID, VARCHAR, INT, REFERENCES ... ON DELETE
-- CASCADE) are valid in both H2 (MODE=PostgreSQL) and PostgreSQL, so this file
-- is identical to the postgresql dialect file.
-- ===========================================================================

ALTER TABLE project   ADD COLUMN deleted_at       TIMESTAMP;
ALTER TABLE project   ADD COLUMN deleted_batch_id UUID;

ALTER TABLE book      ADD COLUMN deleted_at       TIMESTAMP;
ALTER TABLE book      ADD COLUMN deleted_batch_id UUID;

ALTER TABLE chapter   ADD COLUMN deleted_at       TIMESTAMP;
ALTER TABLE chapter   ADD COLUMN deleted_batch_id UUID;

ALTER TABLE scene     ADD COLUMN deleted_at       TIMESTAMP;
ALTER TABLE scene     ADD COLUMN deleted_batch_id UUID;

ALTER TABLE ai_review ADD COLUMN deleted_at       TIMESTAMP;
ALTER TABLE ai_review ADD COLUMN deleted_batch_id UUID;

CREATE INDEX idx_project_deleted_at   ON project(deleted_at);
CREATE INDEX idx_book_deleted_at      ON book(deleted_at);
CREATE INDEX idx_chapter_deleted_at   ON chapter(deleted_at);
CREATE INDEX idx_scene_deleted_at     ON scene(deleted_at);
CREATE INDEX idx_ai_review_deleted_at ON ai_review(deleted_at);

-- One row per delete action (the root the user clicked). user-scoped index for
-- the Trash panel. project_id / project_title are denormalized snapshots so the
-- list can show originating-project context without extra joins.
CREATE TABLE trash_batch (
    id            UUID         NOT NULL PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    root_type     VARCHAR(20)  NOT NULL,
    root_id       UUID         NOT NULL,
    root_title    VARCHAR(500),
    project_id    UUID,
    project_title VARCHAR(255),
    child_count   INT          NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trash_batch_user         ON trash_batch(user_id);
CREATE INDEX idx_trash_batch_user_deleted ON trash_batch(user_id, deleted_at);
