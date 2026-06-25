-- ===========================================================================
-- V24 — Chapter memory documents + memory-document template
--
-- Two independent pieces, both feeding the chapter-wise AI review workflow:
--
--   1. chapter_memory — one author-facing "memory document" per chapter: a
--      standardized summary the author generates explicitly (AI-filled, then
--      optionally hand-edited). When a chapter review runs, the memory docs of
--      all PRECEDING chapters (in linear book order) are concatenated and
--      supplied to the model as continuity context ("story so far"), so the
--      model gets the prior arc without re-reading the entire book. Reviewing a
--      large book one chapter at a time stays tractable.
--
--   2. memory_template — the template that standardizes those summaries. It is
--      author-editable on the SAME mechanism as ai_form_instructions (V20):
--      single-block SELECTION (no inheritance, no concatenation), most-specific
--      first: book -> project -> user global -> system default. The system
--      default is a Java constant (ChapterMemoryTemplateDefaults) and so has no
--      DB row; it carries only the section headers, never a project-specific
--      cast — the author adds their characters in an override.
--
-- All DDL below is valid in both H2 (MODE=PostgreSQL) and PostgreSQL, so this
-- file is identical across the two dialects (as with V14, V17, V20). One ALTER
-- TABLE per column, per the H2 rule.
-- ===========================================================================

-- 1. Generated per-chapter memory documents (one current doc per chapter). ----
--    chapter_id is UNIQUE: regenerating overwrites in place. ON DELETE CASCADE
--    means a hard chapter purge removes its doc; a soft-deleted (trashed)
--    chapter keeps its row but is excluded by the deleted_at filters in the
--    read queries, and the row reappears on restore.
CREATE TABLE chapter_memory (
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

-- 2. Memory-document template overrides (mirrors V20 ai_form_instructions). ----
--    Optional standalone overrides (NULL = no override); they ride the existing
--    soft-delete on project / book for free.
ALTER TABLE project ADD COLUMN memory_template TEXT;
ALTER TABLE book    ADD COLUMN memory_template TEXT;

-- The user's personal editable global. Absent row = fall back to system default.
CREATE TABLE memory_template_global (
    user_id    UUID      NOT NULL PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
