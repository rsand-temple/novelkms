package com.richardsand.novelkms.dao.book;

/**
 * The single source of truth for "linear book order" in SQL.
 *
 * <p>Before V40, four DAOs each carried their own copy of an ordering key built
 * around a {@code sort_bucket} expression:
 *
 * <pre>
 *   CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END AS sort_bucket
 *   ORDER BY sort_bucket, part_order, chapter_order
 * </pre>
 *
 * That key hard-coded "every part-contained chapter precedes every direct-book
 * chapter", which made a direct-book chapter structurally unable to appear
 * before a part — no prologue before Part I. It also silently governed which
 * chapters count as <em>preceding</em> for AI memory/summary context.
 *
 * <p>V40 replaced it with a genuine unified sequence. Parts and direct-book
 * chapters of one book now share one contiguous {@code display_order} range, so
 * a book-level position can be read straight off whichever row owns it:
 *
 * <ul>
 *   <li>{@code book_pos} — the item's position in the book outline. For a
 *       part-contained chapter that is <em>its part's</em> position; for a
 *       direct-book chapter it is the chapter's own. No part and no direct
 *       chapter in the same book share a {@code book_pos}, so this alone
 *       disambiguates between outline entries.</li>
 *   <li>{@code within_pos} — position inside a part, or {@code -1} for a direct
 *       chapter (which has no enclosing part and therefore sorts as if it were
 *       the sole occupant of its outline slot).</li>
 * </ul>
 *
 * <p>The fragments below assume the enclosing query has {@code chapter c} left
 * joined to {@code part p} on {@code c.part_id = p.id}. Every DAO that needs
 * linear book order composes them rather than restating the key, so the four
 * copies can never drift apart again.
 */
public final class BookOrder {

    private BookOrder() {
    }

    /**
     * Ordering-key columns for the innermost CTE. Requires {@code chapter c}
     * LEFT JOINed to {@code part p}.
     */
    public static final String KEY_COLUMNS =
            "    COALESCE(p.display_order, c.display_order) AS book_pos, " +
            "    CASE WHEN c.part_id IS NULL THEN -1 ELSE c.display_order END AS within_pos ";

    /** The key columns as they are re-selected by intermediate CTEs. */
    public static final String KEY_CARRY = "book_pos, within_pos";

    /** The ORDER BY / window ORDER BY clause body. */
    public static final String ORDER_BY = "book_pos, within_pos";

    /**
     * The same ordering, expressed directly against {@code chapter c} /
     * {@code part p} for queries that sort in one pass and have no CTE to carry
     * the derived columns through (e.g. book-wide scene reads).
     */
    public static final String INLINE_ORDER_BY =
            "  COALESCE(p.display_order, c.display_order), " +
            "  CASE WHEN c.part_id IS NULL THEN -1 ELSE c.display_order END ";
}
