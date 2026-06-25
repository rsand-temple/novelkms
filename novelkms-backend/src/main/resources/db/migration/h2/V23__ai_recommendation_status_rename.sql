-- ===========================================================================
-- V23 — AI recommendation status rename (bug-tracker lifecycle)
--
-- The recommendation lifecycle is reframed around a "clear the queue"
-- workflow. Three stored statuses are renamed in place; OPEN, PROMOTED, and
-- DELETED keep their meaning:
--
--     ACCEPTED -> DONE       (acted on, or the manuscript now addresses it)
--     REJECTED -> DISMISSED  (disagree / false positive / stylistic / N/A)
--     FUTURE   -> DEFERRED   (valid, but not now)
--
-- status is a plain VARCHAR, so this is a straight value rewrite. The UI now
-- groups findings as:
--
--     Active   = OPEN + DEFERRED
--     Resolved = DONE + DISMISSED + PROMOTED
--
-- DELETED is retained for legacy rows / admin cleanup and is no longer set
-- from the finding UI; whole-review deletion continues to go through Trash.
--
-- Identical across H2 (MODE=PostgreSQL) and PostgreSQL.
-- ===========================================================================

UPDATE ai_review_recommendation SET status = 'DONE'      WHERE status = 'ACCEPTED';
UPDATE ai_review_recommendation SET status = 'DISMISSED' WHERE status = 'REJECTED';
UPDATE ai_review_recommendation SET status = 'DEFERRED'  WHERE status = 'FUTURE';
