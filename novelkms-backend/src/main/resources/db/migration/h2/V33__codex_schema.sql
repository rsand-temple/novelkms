-- ===========================================================================
-- V33 - Codex category schemas + per-entry structured data
--
-- Stronger typing for codex categories. Some categories (NOTES, WORLD, ...) are
-- fine as a title plus a free rich-text body; others (CHARACTER, VOICE) benefit
-- from a defined set of structured fields that guide the author through the
-- design process and give AI reviews concrete canon to check against.
--
-- The field definitions are DATA, not DDL: a JSON "schema" is stored per
-- category on codex_category, and each codex entry (a scene row) stores its
-- field VALUES as a JSON object in scene.structured_data keyed by the schema
-- field keys. Changing a category's fields -- or, in a future version, letting
-- authors define their own categories and fields -- is then a data edit, never
-- a migration. There are deliberately no mandatory fields.
--
-- The column is named "field_schema" (not "schema") to avoid the SQL SCHEMA
-- keyword in both dialects. One ALTER TABLE per column, per the H2 rule. This
-- file is identical to the postgresql dialect file.
-- ===========================================================================

ALTER TABLE codex_category ADD COLUMN field_schema TEXT;

ALTER TABLE scene ADD COLUMN structured_data TEXT;

-- Seed the two v1 structured categories. Other categories remain schema-less
-- (unstructured title-plus-body), unchanged.
UPDATE codex_category SET field_schema = '{"version":1,"fields":[{"key":"role","label":"Role / Archetype","type":"SELECT","options":["Protagonist","Antagonist","Deuteragonist","Foil","Mentor","Ally","Love interest","Supporting","Minor"],"help":"The structural role this character plays in the story.","feedsAi":true},{"key":"age","label":"Age","type":"SHORT_TEXT","help":"Age or apparent age.","feedsAi":true},{"key":"summary","label":"One-line summary","type":"SHORT_TEXT","help":"A single sentence capturing who this character is.","feedsAi":true},{"key":"want","label":"Want (external goal)","type":"LONG_TEXT","help":"What the character is consciously pursuing.","feedsAi":true},{"key":"need","label":"Need (internal)","type":"LONG_TEXT","help":"What the character actually needs, often unknown to them.","feedsAi":true},{"key":"conflict","label":"Central conflict / flaw","type":"LONG_TEXT","help":"The core flaw or tension driving the character.","feedsAi":true},{"key":"arc","label":"Arc","type":"LONG_TEXT","help":"How the character changes across the story.","feedsAi":true},{"key":"appearance","label":"Appearance","type":"LONG_TEXT","help":"Distinctive physical details worth keeping consistent.","feedsAi":true},{"key":"voice","label":"Voice / speech notes","type":"LONG_TEXT","help":"How this character speaks and sounds on the page.","feedsAi":true},{"key":"relationships","label":"Relationships","type":"LONG_TEXT","help":"Key relationships and how they stand.","feedsAi":true},{"key":"secrets","label":"Secrets","type":"LONG_TEXT","help":"What the character hides, and from whom.","feedsAi":true},{"key":"authorNotes","label":"Author notes (private)","type":"LONG_TEXT","help":"Private notes for you; never shared with the AI.","feedsAi":false}]}' WHERE category_key = 'CHARACTER';
UPDATE codex_category SET field_schema = '{"version":1,"fields":[{"key":"appliesTo","label":"Applies to (character / narrator)","type":"SHORT_TEXT","help":"Whose voice this sheet describes.","feedsAi":true},{"key":"register","label":"Register","type":"SELECT","options":["Formal","Neutral","Casual","Vulgar","Archaic","Mixed"],"help":"The overall formality of this voice.","feedsAi":true},{"key":"diction","label":"Diction & vocabulary","type":"LONG_TEXT","help":"Characteristic word choices and vocabulary level.","feedsAi":true},{"key":"rhythm","label":"Sentence rhythm & length","type":"LONG_TEXT","help":"Typical sentence length and cadence.","feedsAi":true},{"key":"tics","label":"Verbal tics / catchphrases","type":"LONG_TEXT","help":"Repeated phrases, fillers, or habits of speech.","feedsAi":true},{"key":"dialect","label":"Dialect / accent","type":"LONG_TEXT","help":"Regional or class markers rendered on the page.","feedsAi":true},{"key":"dos","label":"Does","type":"LONG_TEXT","help":"Things this voice characteristically does.","feedsAi":true},{"key":"donts","label":"Never does","type":"LONG_TEXT","help":"Things this voice never does.","feedsAi":true},{"key":"samples","label":"Sample lines","type":"LONG_TEXT","help":"A few representative lines in this voice.","feedsAi":true},{"key":"authorNotes","label":"Author notes (private)","type":"LONG_TEXT","help":"Private notes for you; never shared with the AI.","feedsAi":false}]}' WHERE category_key = 'VOICE';
