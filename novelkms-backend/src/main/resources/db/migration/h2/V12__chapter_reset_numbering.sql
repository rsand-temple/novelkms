-- Adds a per-chapter flag that resets the computed chapterNumber to 1 at this
-- chapter, with every subsequent chapter (in book order) continuing the count
-- from there until the next reset point. Defaults to FALSE, which preserves
-- today's single continuous numbering for every existing chapter.
ALTER TABLE chapter ADD COLUMN resets_numbering BOOLEAN NOT NULL DEFAULT FALSE;
