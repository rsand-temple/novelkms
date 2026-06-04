-- =============================================================================
-- NovelKMS  V1 - Initial Schema  (H2)
-- =============================================================================

CREATE TABLE project (
    id            UUID         NOT NULL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------

CREATE TABLE book (
    id            UUID         NOT NULL PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES project(id)  ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    subtitle      VARCHAR(255),
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_book_project ON book(project_id);

-- -----------------------------------------------------------------------------
-- Part is an optional grouping layer between Book and Chapter.
-- Chapters may belong to a Part or directly to a Book (part_id nullable).
-- -----------------------------------------------------------------------------

CREATE TABLE part (
    id            UUID         NOT NULL PRIMARY KEY,
    book_id       UUID         NOT NULL REFERENCES book(id)   ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_part_book ON part(book_id);

-- -----------------------------------------------------------------------------

CREATE TABLE chapter (
    id            UUID         NOT NULL PRIMARY KEY,
    book_id       UUID         NOT NULL REFERENCES book(id)   ON DELETE CASCADE,
    part_id       UUID                  REFERENCES part(id)   ON DELETE SET NULL,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chapter_book ON chapter(book_id);
CREATE INDEX idx_chapter_part ON chapter(part_id);

-- -----------------------------------------------------------------------------
-- Scene is the fundamental unit of authoring.
-- content stores TipTap JSON document.
-- word_count is maintained by the application layer.
-- -----------------------------------------------------------------------------

CREATE TABLE scene (
    id            UUID         NOT NULL PRIMARY KEY,
    chapter_id    UUID         NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    content       TEXT,
    word_count    INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scene_chapter ON scene(chapter_id);
