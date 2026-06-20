-- =============================================================================
-- NovelKMS  V13 - Codex  (PostgreSQL)
-- =============================================================================
-- See the H2 dialect file for the full design notes. Differences here:
--   * book_id is made nullable with DROP NOT NULL (H2 uses SET NULL).
-- =============================================================================

CREATE TABLE codex_category (
    category_key  VARCHAR(50)  NOT NULL PRIMARY KEY,
    label         VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    icon          VARCHAR(50),
    is_default    BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO codex_category (category_key, label, display_order, icon, is_default) VALUES
    ('CHARACTER', 'Characters', 10, 'person',        TRUE),
    ('VOICE',     'Voices',     20, 'record_voice',  TRUE),
    ('PLOT',      'Plot',       30, 'timeline',      TRUE),
    ('WORLD',     'World',      40, 'public',        TRUE),
    ('TIMELINE',  'Timeline',   50, 'schedule',      TRUE),
    ('CANON',     'Canon',      60, 'verified',      TRUE),
    ('NOTES',     'Notes',      70, 'sticky_note',   TRUE);

CREATE TABLE codex (
    id          UUID         NOT NULL PRIMARY KEY,
    project_id  UUID                  REFERENCES project(id) ON DELETE CASCADE,
    book_id     UUID                  REFERENCES book(id)    ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL DEFAULT 'Codex',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_codex_scope CHECK (
        (project_id IS NOT NULL AND book_id IS NULL) OR
        (project_id IS NULL     AND book_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_codex_project ON codex(project_id);
CREATE UNIQUE INDEX uq_codex_book    ON codex(book_id);

ALTER TABLE chapter ALTER COLUMN book_id DROP NOT NULL;

ALTER TABLE chapter ADD COLUMN codex_id UUID;
ALTER TABLE chapter ADD CONSTRAINT fk_chapter_codex
    FOREIGN KEY (codex_id) REFERENCES codex(id) ON DELETE CASCADE;
ALTER TABLE chapter ADD COLUMN codex_category VARCHAR(50);

CREATE INDEX idx_chapter_codex ON chapter(codex_id);
