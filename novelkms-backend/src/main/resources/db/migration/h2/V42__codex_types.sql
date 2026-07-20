-- ===========================================================================
-- V42 - Extensible Codex: per-instance Type fields (Phase E1, schema only)
--
-- Foundation for author-editable Codex Types. Until now a category's field set
-- was SYSTEM-GLOBAL by key: the schema lived once on codex_category.field_schema
-- and every project's CHARACTER type borrowed the same JSON. This migration
-- makes each category INSTANCE (a chapter row with codex_id set, book_id NULL)
-- own its own normalized field set, which is what later phases need for
-- field-level rename / reorder / soft-remove / restore and usage counts.
--
-- WHAT THIS MIGRATION DOES (and only this - no app read path changes yet):
--   1. codex_type_field: one row per field, FK to the Type (category chapter).
--      field_key is IMMUTABLE and, for backfilled rows, preserved VERBATIM from
--      V33 so every existing scene.structured_data value still resolves.
--   2. chapter.codex_type_description: optional per-Type description (E4 writes
--      it; nothing reads it yet). One ALTER per column (H2 rule).
--   3. Backfill: copy the two live seeded schemas (CHARACTER 12 fields, VOICE
--      10 fields) from V33 onto EACH existing instance of those types. Keys,
--      labels, input types, SELECT options, help text, feeds_ai, and array
--      order are taken verbatim from V33. Other categories were schema-less and
--      get no field rows. Trashed instances (deleted_at set) are ALSO backfilled
--      so a restored Type keeps its fields (E7 relies on fields riding along in
--      Trash, never cascading while soft-deleted).
--
-- The literal values below were generated directly from V33__codex_schema.sql;
-- do not hand-edit them out of sync with that file.
--
-- codex_category (master) is NOT touched: it survives as the seedable-default
-- list and the AI-promotion system_key map, and stays live schema authority
-- until E3 cuts the read path over to this table.
--
-- Dialect note: this file differs from its postgresql sibling ONLY in the
-- row-id function used by the backfill (H2 RANDOM_UUID() vs PostgreSQL
-- gen_random_uuid()), exactly as V22/V19 established. All other SQL is plain
-- standard and valid in both.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Normalized per-instance field definitions. chapter_id points at the Type
-- (the category chapter row). deleted_at drives soft-remove/restore (E6); NULL
-- means active. ON DELETE CASCADE fires only on a HARD purge of the chapter -
-- soft-delete (Trash) leaves the chapter row in place, so these rows survive.
-- ---------------------------------------------------------------------------
CREATE TABLE codex_type_field (
    id            UUID         NOT NULL PRIMARY KEY,
    chapter_id    UUID         NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    field_key     VARCHAR(80)  NOT NULL,
    label         VARCHAR(200) NOT NULL,
    input_type    VARCHAR(20)  NOT NULL,                       -- SHORT_TEXT | LONG_TEXT | SELECT
    options       TEXT,                                        -- JSON array for SELECT; NULL otherwise
    help          TEXT,
    feeds_ai      BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP                                    -- NULL = active
);

CREATE UNIQUE INDEX uq_codex_type_field_key     ON codex_type_field(chapter_id, field_key);
CREATE INDEX        idx_codex_type_field_chapter ON codex_type_field(chapter_id);

-- ---------------------------------------------------------------------------
-- Optional per-Type description. Written by the type editor (E4); unread today.
-- ---------------------------------------------------------------------------
ALTER TABLE chapter ADD COLUMN codex_type_description TEXT;

-- ---------------------------------------------------------------------------
-- Backfill the two live seeded schemas onto every existing instance. Values are
-- verbatim from V33; display_order is the field's index in the V33 array.
-- ---------------------------------------------------------------------------
-- CHARACTER: 12 fields (order = array order from V33)
INSERT INTO codex_type_field
    (id, chapter_id, field_key, label, input_type, options, help, feeds_ai, display_order)
SELECT RANDOM_UUID(), c.id, 'role', 'Role / Archetype', 'SELECT', '["Protagonist","Antagonist","Deuteragonist","Foil","Mentor","Ally","Love interest","Supporting","Minor"]', 'The structural role this character plays in the story.', TRUE, 0
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'age', 'Age', 'SHORT_TEXT', NULL, 'Age or apparent age.', TRUE, 1
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'summary', 'One-line summary', 'SHORT_TEXT', NULL, 'A single sentence capturing who this character is.', TRUE, 2
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'want', 'Want (external goal)', 'LONG_TEXT', NULL, 'What the character is consciously pursuing.', TRUE, 3
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'need', 'Need (internal)', 'LONG_TEXT', NULL, 'What the character actually needs, often unknown to them.', TRUE, 4
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'conflict', 'Central conflict / flaw', 'LONG_TEXT', NULL, 'The core flaw or tension driving the character.', TRUE, 5
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'arc', 'Arc', 'LONG_TEXT', NULL, 'How the character changes across the story.', TRUE, 6
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'appearance', 'Appearance', 'LONG_TEXT', NULL, 'Distinctive physical details worth keeping consistent.', TRUE, 7
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'voice', 'Voice / speech notes', 'LONG_TEXT', NULL, 'How this character speaks and sounds on the page.', TRUE, 8
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'relationships', 'Relationships', 'LONG_TEXT', NULL, 'Key relationships and how they stand.', TRUE, 9
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'secrets', 'Secrets', 'LONG_TEXT', NULL, 'What the character hides, and from whom.', TRUE, 10
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'authorNotes', 'Author notes (private)', 'LONG_TEXT', NULL, 'Private notes for you; never shared with the AI.', FALSE, 11
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'CHARACTER';

-- VOICE: 10 fields (order = array order from V33)
INSERT INTO codex_type_field
    (id, chapter_id, field_key, label, input_type, options, help, feeds_ai, display_order)
SELECT RANDOM_UUID(), c.id, 'appliesTo', 'Applies to (character / narrator)', 'SHORT_TEXT', NULL, 'Whose voice this sheet describes.', TRUE, 0
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'register', 'Register', 'SELECT', '["Formal","Neutral","Casual","Vulgar","Archaic","Mixed"]', 'The overall formality of this voice.', TRUE, 1
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'diction', 'Diction & vocabulary', 'LONG_TEXT', NULL, 'Characteristic word choices and vocabulary level.', TRUE, 2
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'rhythm', 'Sentence rhythm & length', 'LONG_TEXT', NULL, 'Typical sentence length and cadence.', TRUE, 3
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'tics', 'Verbal tics / catchphrases', 'LONG_TEXT', NULL, 'Repeated phrases, fillers, or habits of speech.', TRUE, 4
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'dialect', 'Dialect / accent', 'LONG_TEXT', NULL, 'Regional or class markers rendered on the page.', TRUE, 5
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'dos', 'Does', 'LONG_TEXT', NULL, 'Things this voice characteristically does.', TRUE, 6
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'donts', 'Never does', 'LONG_TEXT', NULL, 'Things this voice never does.', TRUE, 7
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'samples', 'Sample lines', 'LONG_TEXT', NULL, 'A few representative lines in this voice.', TRUE, 8
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE'
UNION ALL
SELECT RANDOM_UUID(), c.id, 'authorNotes', 'Author notes (private)', 'LONG_TEXT', NULL, 'Private notes for you; never shared with the AI.', FALSE, 9
    FROM chapter c WHERE c.codex_id IS NOT NULL AND c.codex_category = 'VOICE';
