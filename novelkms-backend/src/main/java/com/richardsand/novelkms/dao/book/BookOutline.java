package com.richardsand.novelkms.dao.book;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.model.book.OutlineItemType;
import com.richardsand.novelkms.model.book.OutlineRef;

/**
 * Write primitives for a book's outline: the one contiguous {@code display_order} sequence shared by the book's {@code part} rows and its direct-book
 * {@code chapter} rows ({@code part_id IS NULL AND codex_id IS NULL}).
 *
 * <p>
 * These are static and take a {@link Connection} rather than living on a DAO with its own {@code DataSource}, because both {@code ChapterDao} and
 * {@code PartDao} must be able to allocate an outline position inside their own insert transaction. Making this a DAO instead would force a circular dependency
 * between the two DAOs or a DI change in {@code NovelKmsServer}; a Connection-scoped helper avoids both and keeps every outline write in one file.
 *
 * <p>
 * Soft-deleted direct-book chapters are deliberately <em>included</em> when allocating and shifting positions. They keep occupying their slot, which guarantees
 * a trashed chapter can never collide with a live part after a restore, at the cost of a harmless gap in the live sequence. Gaps are fine: the read-side key in
 * {@link BookOrder} only ever compares positions, never assumes they are dense. Parts have no soft-delete at all (they are excluded from Trash by the
 * promote-children rule), so there is no equivalent case there.
 */
public final class BookOutline {

    private BookOutline() {
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /**
     * Returns the book's outline entries — parts and direct-book chapters together — in linear order.
     */
    public static List<OutlineRef> find(Connection c, UUID bookId) throws SQLException {
        String sql = """
                SELECT id, kind, display_order FROM (
                    SELECT id, 'PART' AS kind, display_order
                    FROM part
                    WHERE book_id = ?
                    UNION ALL
                    SELECT id, 'CHAPTER' AS kind, display_order
                    FROM chapter
                    WHERE book_id = ? AND part_id IS NULL AND codex_id IS NULL AND deleted_at IS NULL
                ) outline
                ORDER BY display_order
                """;
        List<OutlineRef> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setObject(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new OutlineRef(OutlineItemType.valueOf(rs.getString("kind")), rs.getObject("id", UUID.class)));
                }
            }
        }
        return result;
    }

    /**
     * Returns the next free position at the end of the book's outline: one past the highest position held by any part or direct-book chapter, trashed or not.
     */
    public static int nextPosition(Connection c, UUID bookId) throws SQLException {
        String sql = """
                SELECT COALESCE(MAX(pos), -1) + 1 FROM (
                    SELECT display_order AS pos FROM part WHERE book_id = ?
                    UNION ALL
                    SELECT display_order AS pos FROM chapter
                    WHERE book_id = ? AND part_id IS NULL AND codex_id IS NULL
                ) outline
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setObject(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Insertion
    // -------------------------------------------------------------------------

    /**
     * Opens a slot in the book's outline immediately before or after the anchor item, and returns the position the new item should take.
     *
     * <p>
     * The anchor may be either a part or a direct-book chapter of this book — the caller does not have to know which, which is what lets the UI offer "Insert
     * Chapter Before" on a part node and "Insert Part After" on a chapter node from the same code path.
     *
     * <p>
     * If the anchor cannot be resolved (wrong book, part-contained chapter, stale ID), this appends to the end rather than failing. A misplaced new chapter is
     * trivially draggable; a 500 on the create path is not.
     *
     * @param anchorId
     *            the outline item to insert relative to
     * @param before
     *            true = take the anchor's position and push it down; false = take the position just after the anchor
     */
    public static int openSlot(Connection c, UUID bookId, UUID anchorId, boolean before) throws SQLException {
        Integer anchorPos = positionOf(c, bookId, anchorId);
        if (anchorPos == null) {
            return nextPosition(c, bookId);
        }
        int target = before ? anchorPos : anchorPos + 1;
        shiftFrom(c, bookId, target);
        return target;
    }

    /**
     * Resolves an outline item's position, whichever table it lives in. Returns null when the ID is not an outline item of this book.
     */
    private static Integer positionOf(Connection c, UUID bookId, UUID anchorId) throws SQLException {
        if (anchorId == null) {
            return null;
        }
        String sql = """
                SELECT display_order FROM (
                    SELECT id, display_order FROM part WHERE book_id = ?
                    UNION ALL
                    SELECT id, display_order FROM chapter
                    WHERE book_id = ? AND part_id IS NULL AND codex_id IS NULL
                ) outline
                WHERE id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setObject(2, bookId);
            ps.setObject(3, anchorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /**
     * Pushes every outline item at or after {@code from} down by one, across both tables, to free {@code from} for a new item.
     */
    private static void shiftFrom(Connection c, UUID bookId, int from) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());

        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE part SET display_order = display_order + 1, updated_at = ?
                WHERE book_id = ? AND display_order >= ?
                """)) {
            ps.setTimestamp(1, now);
            ps.setObject(2, bookId);
            ps.setInt(3, from);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE chapter SET display_order = display_order + 1, updated_at = ?
                WHERE book_id = ? AND part_id IS NULL AND codex_id IS NULL AND display_order >= ?
                """)) {
            ps.setTimestamp(1, now);
            ps.setObject(2, bookId);
            ps.setInt(3, from);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Reordering
    // -------------------------------------------------------------------------

    /**
     * Assigns positions 0..n-1 to the supplied outline items in the order given, writing each one back to the table its type names.
     *
     * <p>
     * The {@code book_id} guard on both statements means a stale or forged ID list can only ever renumber rows of the book named in the path — which the tenant
     * filter has already proven the caller owns.
     *
     * <p>
     * Callers are responsible for the transaction. Items not present in the list keep their current position, so the caller should always send the complete
     * outline.
     */
    public static void applyOrder(Connection c, UUID bookId, List<OutlineRef> items) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());

        // The position an item takes is its index in the merged list, so parts and
        // chapters draw from one shared 0..n-1 range even though they update two
        // different tables. Capture (index, id) per type first, then run each
        // table's batch in its own statement scope.
        //
        // Each PreparedStatement lives and dies inside a single try — never two held
        // open across one loop. Under DBCP2 statement pooling
        // (maxOpenPreparedStatements is set) a statement handed back from the pool
        // can be a recycled instance, and juggling two of them across a mixed batch
        // is what produced "PreparedStatement ... is closed". This mirrors
        // applyPartOrder, the one-statement pattern every other batch DAO here uses.
        List<UUID> partIds = new ArrayList<>();
        List<Integer> partPositions = new ArrayList<>();
        List<UUID> chapterIds = new ArrayList<>();
        List<Integer> chapterPositions = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            OutlineRef item = items.get(i);
            if (item == null || item.id() == null || item.type() == null) {
                continue;
            }
            if (item.type() == OutlineItemType.PART) {
                partIds.add(item.id());
                partPositions.add(i);
            } else {
                chapterIds.add(item.id());
                chapterPositions.add(i);
            }
        }

        if (!partIds.isEmpty()) {
            String partSql = """
                    UPDATE part SET display_order = ?, updated_at = ?
                    WHERE id = ? AND book_id = ?
                    """;
            try (PreparedStatement ps = c.prepareStatement(partSql)) {
                for (int k = 0; k < partIds.size(); k++) {
                    ps.setInt(1, partPositions.get(k));
                    ps.setTimestamp(2, now);
                    ps.setObject(3, partIds.get(k));
                    ps.setObject(4, bookId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        if (!chapterIds.isEmpty()) {
            String chapterSql = """
                    UPDATE chapter SET display_order = ?, updated_at = ?
                    WHERE id = ? AND book_id = ? AND part_id IS NULL AND codex_id IS NULL
                    """;
            try (PreparedStatement ps = c.prepareStatement(chapterSql)) {
                for (int k = 0; k < chapterIds.size(); k++) {
                    ps.setInt(1, chapterPositions.get(k));
                    ps.setTimestamp(2, now);
                    ps.setObject(3, chapterIds.get(k));
                    ps.setObject(4, bookId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }
}