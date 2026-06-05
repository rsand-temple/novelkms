-- =============================================================================
-- NovelKMS  V1 - Initial Schema  (PostgreSQL)
-- =============================================================================

CREATE TABLE project (
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------

CREATE TABLE book (
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID         NOT NULL REFERENCES project(id)  ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    subtitle      VARCHAR(255),
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_book_project ON book(project_id);

-- -----------------------------------------------------------------------------

CREATE TABLE part (
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id       UUID         NOT NULL REFERENCES book(id)   ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_part_book ON part(book_id);

-- -----------------------------------------------------------------------------

CREATE TABLE chapter (
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id       UUID         NOT NULL REFERENCES book(id)   ON DELETE CASCADE,
    part_id       UUID                  REFERENCES part(id)   ON DELETE SET NULL,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chapter_book ON chapter(book_id);
CREATE INDEX idx_chapter_part ON chapter(part_id);

-- -----------------------------------------------------------------------------

CREATE TABLE scene (
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id    UUID         NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    content       TEXT,
    word_count    INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scene_chapter ON scene(chapter_id);
