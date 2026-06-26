-- ===========================================================================
-- V25 — Book / chapter summaries
--
-- A new AI artifact family, completely independent of memory documents (V24).
-- Two pieces:
--
--   1. chapter_summary — one human-readable summary paragraph per chapter,
--      generated explicitly by the author (AI-filled from the chapter prose, then
--      optionally hand-edited). Surfaced in the book's aggregated, read-only
--      chapter-summary view, and consumed (in linear book order) as the SOLE
--      input when generating the book summary.
--
--   2. book_summary — one synopsis per book of no more than ~1000 words,
--      generated ENTIRELY from the chapter summaries (never the manuscript prose
--      directly), since a full-length book is too large to summarize reliably in
--      one pass. Optionally hand-edited afterward.
--
-- Both are one-per-parent (UNIQUE on the parent id) and overwrite on regenerate,
-- exactly like chapter_memory. Trashed chapters/books keep their rows (excluded
-- by the deleted_at filters in the read queries) and a hard purge cascades them
-- away. All DDL is valid in both H2 (MODE=PostgreSQL) and PostgreSQL, so this
-- file is identical across the two dialects (as with V24).
-- ===========================================================================

-- 1. Per-chapter summary (one current summary per chapter). -------------------
CREATE TABLE chapter_summary (
    id             UUID        NOT NULL PRIMARY KEY,
    chapter_id     UUID        NOT NULL UNIQUE REFERENCES chapter(id) ON DELETE CASCADE,
    content        TEXT        NOT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',   -- AI | EDITED
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Per-book summary (one current summary per book). -------------------------
CREATE TABLE book_summary (
    id             UUID        NOT NULL PRIMARY KEY,
    book_id        UUID        NOT NULL UNIQUE REFERENCES book(id) ON DELETE CASCADE,
    content        TEXT        NOT NULL,
    word_count     INTEGER     NOT NULL DEFAULT 0,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',   -- AI | EDITED
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
