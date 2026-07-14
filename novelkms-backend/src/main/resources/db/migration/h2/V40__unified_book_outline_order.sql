-- V40 — Unified book outline ordering.
--
-- Before this migration, `part.display_order` and `chapter.display_order`
-- (for direct-book chapters, i.e. part_id IS NULL) were two INDEPENDENT
-- sequences that never compared with each other. Linear book order was
-- reconstructed at read time with a `sort_bucket` expression that placed every
-- part-contained chapter ahead of every direct-book chapter. A direct-book
-- chapter therefore could not be positioned before a part: no prologue ahead of
-- Part I, and (less visibly) no correct notion of which chapters "precede" a
-- given chapter for AI memory/summary context.
--
-- From V40 the parts and the direct-book chapters of a single book share ONE
-- contiguous display_order sequence — the "book outline". Chapters inside a part
-- keep their own separate 0..M-1 sequence, unchanged.
--
-- This migration changes no schema. It renumbers existing rows into the new
-- shared sequence, preserving today's exact visible order: parts take positions
-- 0..P-1 and direct-book chapters take P..P+D-1. Every existing book therefore
-- looks identical after upgrading. Authors reposition a prologue by dragging it
-- once.
--
-- Soft-deleted direct-book chapters are included in the renumbering on purpose.
-- They keep occupying a slot, so a restore can never collide with a part.
-- Ties on display_order are broken by id, deterministically.

UPDATE part p
SET display_order = (
    SELECT COUNT(*)
    FROM part p2
    WHERE p2.book_id = p.book_id
      AND (p2.display_order < p.display_order
           OR (p2.display_order = p.display_order AND p2.id < p.id))
);

UPDATE chapter c
SET display_order = (
        SELECT COUNT(*)
        FROM part p
        WHERE p.book_id = c.book_id
    ) + (
        SELECT COUNT(*)
        FROM chapter c2
        WHERE c2.book_id = c.book_id
          AND c2.part_id IS NULL
          AND c2.codex_id IS NULL
          AND (c2.display_order < c.display_order
               OR (c2.display_order = c.display_order AND c2.id < c.id))
    )
WHERE c.book_id IS NOT NULL
  AND c.part_id IS NULL
  AND c.codex_id IS NULL;
