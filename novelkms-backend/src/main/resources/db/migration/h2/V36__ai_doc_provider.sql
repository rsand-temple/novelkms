-- ===========================================================================
-- V36 — Provider dimension for the per-parent AI document families
--
-- Until now each of the four "one current document per parent" AI artifact
-- families held at most one row per parent:
--
--     chapter_memory     — UNIQUE (chapter_id)
--     chapter_summary    — UNIQUE (chapter_id)
--     chapter_editorial  — UNIQUE (chapter_id)
--     book_summary       — UNIQUE (book_id)
--
-- A user can configure keys for more than one provider (OpenAI, Anthropic,
-- Gemini) and now wants each provider to keep its OWN memory / summary /
-- editorial / book-summary per parent, toggling between them in the UI. So the
-- shape becomes one document per (parent, provider): a new NOT NULL `provider`
-- column, and the single-column parent UNIQUE is replaced by a composite
-- (parent_id, provider) UNIQUE.
--
-- ai_review is deliberately NOT touched here: it already carries a NOT NULL
-- `provider` column (V14) and is append-only (many rows per chapter), so it
-- already supports provider variance without change.
--
-- Backfill: every pre-existing row predates multi-provider support and was
-- produced by the only provider available when it was written; per the agreed
-- design all legacy rows are stamped 'OPENAI'.
--
-- H2 vs PostgreSQL divergence
-- ---------------------------
-- The original parent UNIQUE was declared INLINE on the column
-- (`chapter_id UUID NOT NULL UNIQUE ...`), so H2 assigned it a
-- non-deterministic generated name — there is no portable, name-free way to
-- DROP it in H2. Rather than ship dynamic constraint-name lookup that cannot be
-- verified without a live H2 (Maven cannot run in this environment), this H2
-- file REBUILDS each table: create the new shape, copy the rows (stamping
-- 'OPENAI'), drop the old table, rename into place. These four tables hold
-- regenerable AI artifacts (never manuscript prose, which lives in `scene`), so
-- a rebuild is low-risk, and every statement here is plain, deterministic H2
-- DDL/DML. The PostgreSQL dialect file does the equivalent in place with a
-- name-free `DO` block (see the postgresql/ copy). A useful side effect: after
-- the rebuild the parent UNIQUE finally has a known, explicit name.
--
-- No table here has any inbound foreign key or secondary index, so DROP/RENAME
-- is safe and nothing else needs recreating.
-- ===========================================================================

-- 1. chapter_memory ---------------------------------------------------------
CREATE TABLE chapter_memory_v36 (
    id             UUID        NOT NULL PRIMARY KEY,
    chapter_id     UUID        NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    provider       VARCHAR(40) NOT NULL,
    content        TEXT        NOT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    user_guidance  TEXT,
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chapter_memory_chapter_provider UNIQUE (chapter_id, provider)
);
INSERT INTO chapter_memory_v36
    (id, chapter_id, provider, content, source, prompt_version, model, user_guidance,
     generated_at, created_at, updated_at)
    SELECT id, chapter_id, 'OPENAI', content, source, prompt_version, model, user_guidance,
           generated_at, created_at, updated_at
    FROM chapter_memory;
DROP TABLE chapter_memory;
ALTER TABLE chapter_memory_v36 RENAME TO chapter_memory;

-- 2. chapter_summary --------------------------------------------------------
CREATE TABLE chapter_summary_v36 (
    id             UUID        NOT NULL PRIMARY KEY,
    chapter_id     UUID        NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    provider       VARCHAR(40) NOT NULL,
    content        TEXT        NOT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    user_guidance  TEXT,
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chapter_summary_chapter_provider UNIQUE (chapter_id, provider)
);
INSERT INTO chapter_summary_v36
    (id, chapter_id, provider, content, source, prompt_version, model, user_guidance,
     generated_at, created_at, updated_at)
    SELECT id, chapter_id, 'OPENAI', content, source, prompt_version, model, user_guidance,
           generated_at, created_at, updated_at
    FROM chapter_summary;
DROP TABLE chapter_summary;
ALTER TABLE chapter_summary_v36 RENAME TO chapter_summary;

-- 3. book_summary -----------------------------------------------------------
CREATE TABLE book_summary_v36 (
    id             UUID        NOT NULL PRIMARY KEY,
    book_id        UUID        NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    provider       VARCHAR(40) NOT NULL,
    content        TEXT        NOT NULL,
    word_count     INTEGER     NOT NULL DEFAULT 0,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    user_guidance  TEXT,
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_book_summary_book_provider UNIQUE (book_id, provider)
);
INSERT INTO book_summary_v36
    (id, book_id, provider, content, word_count, source, prompt_version, model, user_guidance,
     generated_at, created_at, updated_at)
    SELECT id, book_id, 'OPENAI', content, word_count, source, prompt_version, model, user_guidance,
           generated_at, created_at, updated_at
    FROM book_summary;
DROP TABLE book_summary;
ALTER TABLE book_summary_v36 RENAME TO book_summary;

-- 4. chapter_editorial ------------------------------------------------------
CREATE TABLE chapter_editorial_v36 (
    id             UUID        NOT NULL PRIMARY KEY,
    chapter_id     UUID        NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    provider       VARCHAR(40) NOT NULL,
    content        TEXT        NOT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    user_guidance  TEXT,
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chapter_editorial_chapter_provider UNIQUE (chapter_id, provider)
);
INSERT INTO chapter_editorial_v36
    (id, chapter_id, provider, content, source, prompt_version, model, user_guidance,
     generated_at, created_at, updated_at)
    SELECT id, chapter_id, 'OPENAI', content, source, prompt_version, model, user_guidance,
           generated_at, created_at, updated_at
    FROM chapter_editorial;
DROP TABLE chapter_editorial;
ALTER TABLE chapter_editorial_v36 RENAME TO chapter_editorial;
