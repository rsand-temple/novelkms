-- ===========================================================================
-- V34 — Chapter editorial notes
--
-- A new author-facing AI artifact family, one per chapter. An "editorial" is a
-- short editorial reading of a chapter — tone, genre drift, character arcs,
-- storyline evolution, the AI's overall "what do you think?" — deliberately NOT
-- a line-level review: it does not surface spelling, grammar, or fine findings
-- (that is what ai_review is for), and it is kept brief (half a page or less).
--
-- Unlike memory documents (V24), an editorial is NEVER consumed by any other AI
-- function — it is purely for the author's edification. It does, however, use
-- the SAME context inputs as a chapter review when it is generated (preceding
-- chapters' memory documents as "story so far", plus any pinned Codex reference
-- entries). Generating an editorial never touches a memory document, a summary,
-- or a review, and vice versa.
--
-- Shape mirrors chapter_summary (V25) exactly, including the user_guidance
-- column that V26 added to the other artifact families: one current editorial
-- per chapter (chapter_id UNIQUE), overwrite on regenerate, AI | EDITED source,
-- content stored as authored TipTap HTML (edited in EditorPanel via the nav
-- leaf, the same as memory/summary — see V27). Trashed chapters keep their row
-- (excluded by the deleted_at read filters) and a hard purge cascades it away.
--
-- All DDL below is valid in both H2 (MODE=PostgreSQL) and PostgreSQL, so this
-- file is identical across the two dialects (as with V24/V25). The
-- user_guidance column is included in the CREATE here rather than added by a
-- later ALTER, since this family is introduced after V26.
-- ===========================================================================

CREATE TABLE chapter_editorial (
    id             UUID        NOT NULL PRIMARY KEY,
    chapter_id     UUID        NOT NULL UNIQUE REFERENCES chapter(id) ON DELETE CASCADE,
    content        TEXT        NOT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'AI',   -- AI | EDITED
    prompt_version VARCHAR(40),
    model          VARCHAR(120),
    user_guidance  TEXT,
    generated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
