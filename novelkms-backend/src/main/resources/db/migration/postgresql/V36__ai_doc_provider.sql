-- ===========================================================================
-- V36 — Provider dimension for the per-parent AI document families
--
-- See the h2/ dialect copy for the full rationale. In short: each of the four
-- "one current document per parent" AI artifact families becomes one document
-- per (parent, provider). A new NOT NULL `provider` column is added, existing
-- rows are backfilled to 'OPENAI' (the only provider available when they were
-- written), and the single-column parent UNIQUE is replaced by a composite
-- (parent_id, provider) UNIQUE.
--
-- ai_review is untouched: it already has a NOT NULL `provider` column (V14) and
-- is append-only.
--
-- PostgreSQL does this IN PLACE. The original parent UNIQUE was declared inline
-- on the column, so PostgreSQL named it deterministically
-- (`<table>_<column>_key`); even so, a name-free `DO` block looks it up by
-- table + type ('u') and drops whatever it finds, which is robust against any
-- earlier manual rename. Each table has exactly one UNIQUE constraint (the
-- parent one), so the lookup is unambiguous. (The H2 dialect cannot do this
-- name-free and rebuilds the table instead.)
-- ===========================================================================

-- 1. chapter_memory ---------------------------------------------------------
ALTER TABLE chapter_memory ADD COLUMN provider VARCHAR(40);
UPDATE chapter_memory SET provider = 'OPENAI' WHERE provider IS NULL;
ALTER TABLE chapter_memory ALTER COLUMN provider SET NOT NULL;
DO $$
DECLARE cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'chapter_memory'::regclass AND contype = 'u';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE chapter_memory DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;
ALTER TABLE chapter_memory
    ADD CONSTRAINT uq_chapter_memory_chapter_provider UNIQUE (chapter_id, provider);

-- 2. chapter_summary --------------------------------------------------------
ALTER TABLE chapter_summary ADD COLUMN provider VARCHAR(40);
UPDATE chapter_summary SET provider = 'OPENAI' WHERE provider IS NULL;
ALTER TABLE chapter_summary ALTER COLUMN provider SET NOT NULL;
DO $$
DECLARE cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'chapter_summary'::regclass AND contype = 'u';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE chapter_summary DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;
ALTER TABLE chapter_summary
    ADD CONSTRAINT uq_chapter_summary_chapter_provider UNIQUE (chapter_id, provider);

-- 3. book_summary -----------------------------------------------------------
ALTER TABLE book_summary ADD COLUMN provider VARCHAR(40);
UPDATE book_summary SET provider = 'OPENAI' WHERE provider IS NULL;
ALTER TABLE book_summary ALTER COLUMN provider SET NOT NULL;
DO $$
DECLARE cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'book_summary'::regclass AND contype = 'u';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE book_summary DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;
ALTER TABLE book_summary
    ADD CONSTRAINT uq_book_summary_book_provider UNIQUE (book_id, provider);

-- 4. chapter_editorial ------------------------------------------------------
ALTER TABLE chapter_editorial ADD COLUMN provider VARCHAR(40);
UPDATE chapter_editorial SET provider = 'OPENAI' WHERE provider IS NULL;
ALTER TABLE chapter_editorial ALTER COLUMN provider SET NOT NULL;
DO $$
DECLARE cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'chapter_editorial'::regclass AND contype = 'u';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE chapter_editorial DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;
ALTER TABLE chapter_editorial
    ADD CONSTRAINT uq_chapter_editorial_chapter_provider UNIQUE (chapter_id, provider);
