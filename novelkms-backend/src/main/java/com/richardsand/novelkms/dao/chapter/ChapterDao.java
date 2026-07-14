package com.richardsand.novelkms.dao.chapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.dao.book.BookOrder;
import com.richardsand.novelkms.dao.book.BookOutline;
import com.richardsand.novelkms.model.book.OutlineRef;
import com.richardsand.novelkms.model.chapter.Chapter;

public class ChapterDao {

    private final BasicDataSource ds;

    public ChapterDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Chapter map(ResultSet rs) throws SQLException {
        String rawPartId = rs.getString("part_id");
        UUID   partId    = rawPartId != null ? UUID.fromString(rawPartId) : null;
        return Chapter.builder()
                .id(rs.getObject("id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .partId(partId)
                .codexId(rs.getObject("codex_id", UUID.class))
                .codexCategory(rs.getString("codex_category"))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .resetsNumbering(rs.getBoolean("resets_numbering"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .chapterNumber(rs.getInt("chapter_number"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Book-wide numbering CTE
    //
    // Chapter numbers are computed, never stored. The numbering walks the book in
    // linear order (see BookOrder: since V40 parts and direct-book chapters share
    // one display_order sequence, so a prologue placed before Part I is numbered
    // before Part I's chapters) and restarts at 1 after every chapter flagged
    // resets_numbering.
    //
    // The clause is assembled once here and reused by all three chapter reads, so
    // the nav tree, the editor, and the exports cannot disagree about book order.
    // -------------------------------------------------------------------------

    /**
     * @param scopePredicate a SQL predicate selecting the book's chapters,
     *                       parameterized by the caller
     */
    private static String numberingCte(String scopePredicate) {
        return "WITH ordered AS ( "
                + "  SELECT c.id, c.resets_numbering, "
                + BookOrder.KEY_COLUMNS
                + "  FROM chapter c "
                + "  LEFT JOIN part p ON c.part_id = p.id "
                + "  WHERE " + scopePredicate + " AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "), "
                + "grouped AS ( "
                + "  SELECT id, " + BookOrder.KEY_CARRY + ", "
                + "    SUM(CASE WHEN resets_numbering THEN 1 ELSE 0 END) OVER ( "
                + "      ORDER BY " + BookOrder.ORDER_BY + " "
                + "      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW "
                + "    ) AS numbering_group "
                + "  FROM ordered "
                + "), "
                + "numbered AS ( "
                + "  SELECT id, "
                + "    ROW_NUMBER() OVER ( "
                + "      PARTITION BY numbering_group "
                + "      ORDER BY " + BookOrder.ORDER_BY + " "
                + "    ) AS chapter_number "
                + "  FROM grouped "
                + ") ";
    }

    private static final String CHAPTER_COLUMNS =
            "SELECT c.id, c.book_id, c.part_id, c.codex_id, c.codex_category, "
            + "       c.title, c.subtitle, c.notes, c.resets_numbering, "
            + "       c.display_order, c.created_at, c.updated_at, ";

    // -------------------------------------------------------------------------
    // Queries (manuscript chapters only — codex chapters are excluded)
    // -------------------------------------------------------------------------

    /**
     * The book's direct chapters — those not inside any part. These are outline
     * items: they interleave with the book's parts in one shared display_order
     * sequence, so this list may begin before, end after, or sit between parts.
     */
    public List<Chapter> findByBookId(UUID bookId) throws SQLException {
        String sql = numberingCte("c.book_id = ?")
                + CHAPTER_COLUMNS
                + "       n.chapter_number "
                + "FROM chapter c "
                + "JOIN numbered n ON c.id = n.id "
                + "WHERE c.book_id = ? AND c.part_id IS NULL AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "ORDER BY c.display_order";

        List<Chapter> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, bookId); // ordered CTE
            ps.setObject(2, bookId); // outer WHERE
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Chapter> findByPartId(UUID partId) throws SQLException {
        String sql = numberingCte("c.book_id = (SELECT book_id FROM part WHERE id = ?)")
                + CHAPTER_COLUMNS
                + "       n.chapter_number "
                + "FROM chapter c "
                + "JOIN numbered n ON c.id = n.id "
                + "WHERE c.part_id = ? AND c.deleted_at IS NULL "
                + "ORDER BY c.display_order";

        List<Chapter> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, partId); // ordered CTE subquery
            ps.setObject(2, partId); // outer WHERE
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /**
     * Returns the category chapters that belong to a codex, in display order.
     * Codex chapters are not numbered, so chapter_number is reported as 0.
     */
    public List<Chapter> findByCodexId(UUID codexId) throws SQLException {
        String sql = "SELECT c.id, c.book_id, c.part_id, c.codex_id, c.codex_category, " +
                "       c.title, c.subtitle, c.notes, c.resets_numbering, " +
                "       c.display_order, c.created_at, c.updated_at, " +
                "       0 AS chapter_number " +
                "FROM chapter c " +
                "WHERE c.codex_id = ? AND c.deleted_at IS NULL " +
                "ORDER BY c.display_order";

        List<Chapter> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, codexId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Optional<Chapter> findById(UUID id) throws SQLException {
        // The numbering CTE is keyed on book_id, so it produces no row for a
        // codex chapter (book_id NULL). The final LEFT JOIN + COALESCE therefore
        // returns codex chapters with chapter_number 0, while manuscript chapters
        // keep their computed number.
        String sql = numberingCte("c.book_id = (SELECT book_id FROM chapter WHERE id = ?)")
                + CHAPTER_COLUMNS
                + "       COALESCE(n.chapter_number, 0) AS chapter_number "
                + "FROM chapter c "
                + "LEFT JOIN numbered n ON c.id = n.id "
                + "WHERE c.id = ? AND c.deleted_at IS NULL";

        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id); // ordered CTE subquery
            ps.setObject(2, id); // outer WHERE
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Creates a chapter at the end of its container.
     *
     * <p>A part-contained chapter appends to that part's own sequence. A
     * direct-book chapter appends to the end of the <em>book outline</em> — past
     * the last part as well as the last direct chapter — because parts and
     * direct chapters share one sequence.
     */
    public Chapter create(UUID bookId, UUID partId, String title, String subtitle, String notes)
            throws SQLException {
        return insert(bookId, partId, title, subtitle, notes, null, false);
    }

    /**
     * Creates a chapter immediately before or after an existing sibling.
     *
     * <p>For a direct-book chapter ({@code partId == null}) the anchor is an
     * outline item and may be either a part or another direct-book chapter —
     * which is what allows "Insert Chapter Before" on Part I to produce a
     * prologue. For a part-contained chapter the anchor is a sibling chapter in
     * the same part.
     *
     * <p>An anchor that cannot be resolved falls back to appending rather than
     * failing: a chapter in the wrong slot is one drag away from right, whereas
     * an error on create loses what the author typed.
     */
    public Chapter createRelativeTo(UUID bookId, UUID partId, String title, String subtitle, String notes,
            UUID anchorId, boolean before) throws SQLException {
        return insert(bookId, partId, title, subtitle, notes, anchorId, before);
    }

    private Chapter insert(UUID bookId, UUID partId, String title, String subtitle, String notes,
            UUID anchorId, boolean before) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();

        String sql = """
                INSERT INTO chapter (id, book_id, part_id, title, subtitle, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int displayOrder;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Opening a slot shifts existing siblings, so the shift and the
                // insert have to land together or not at all.
                if (partId != null) {
                    displayOrder = anchorId != null
                            ? openSlotInPart(c, partId, anchorId, before)
                            : nextDisplayOrderInPart(c, partId);
                } else {
                    displayOrder = anchorId != null
                            ? BookOutline.openSlot(c, bookId, anchorId, before)
                            : BookOutline.nextPosition(c, bookId);
                }

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, id);
                    ps.setObject(2, bookId);
                    ps.setObject(3, partId);
                    ps.setString(4, title);
                    ps.setString(5, subtitle);
                    ps.setInt(6, displayOrder);
                    ps.setString(7, notes);
                    ps.setTimestamp(8, Timestamp.from(now));
                    ps.setTimestamp(9, Timestamp.from(now));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        return Chapter.builder()
                .id(id).bookId(bookId).partId(partId).title(title).subtitle(subtitle)
                .displayOrder(displayOrder).notes(notes)
                .resetsNumbering(false)
                .createdAt(now).updatedAt(now)
                .build();
    }

    /**
     * Creates a category chapter inside a codex. book_id and part_id are NULL;
     * codex_id and codex_category identify it. display_order is scoped to the
     * codex and has nothing to do with the book outline.
     */
    public Chapter createCodexChapter(UUID codexId, String codexCategory, String title) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String  sql = """
                INSERT INTO chapter (id, book_id, part_id, codex_id, codex_category, title, subtitle, display_order, notes, created_at, updated_at)
                VALUES (?, NULL, NULL, ?, ?, ?, NULL, ?, NULL, ?, ?)
                """;
        int displayOrder;
        try (Connection c = ds.getConnection()) {
            displayOrder = nextDisplayOrderInCodex(c, codexId);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, codexId);
                ps.setString(3, codexCategory);
                ps.setString(4, title);
                ps.setInt(5, displayOrder);
                ps.setTimestamp(6, Timestamp.from(now));
                ps.setTimestamp(7, Timestamp.from(now));
                ps.executeUpdate();
            }
        }
        return Chapter.builder()
                .id(id).codexId(codexId).codexCategory(codexCategory).title(title)
                .displayOrder(displayOrder)
                .resetsNumbering(false)
                .createdAt(now).updatedAt(now)
                .chapterNumber(0)
                .build();
    }

    public Optional<Chapter> update(UUID id, String title, String subtitle, String notes,
            boolean resetsNumbering) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE chapter SET title = ?, subtitle = ?, notes = ?, resets_numbering = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, subtitle);
            ps.setString(3, notes);
            ps.setBoolean(4, resetsNumbering);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setObject(6, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    public boolean delete(UUID id) throws SQLException {
        String sql = "DELETE FROM chapter WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Ordering
    //
    // Book-level ordering is NOT here — it spans two tables and lives in
    // BookOutline / BookOutlineDao. The old reorderInBook(bookId, ids) was
    // removed with V40: renumbering the direct chapters 0..n-1 in isolation now
    // collides with the parts interleaved among them.
    // -------------------------------------------------------------------------

    /**
     * Assigns display_order 0..n-1 to the given chapter IDs within a part.
     * The part_id guard prevents accidental updates to chapters in a different part.
     */
    public void reorderInPart(UUID partId, List<UUID> ids) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                applyPartOrder(c, partId, ids);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void applyPartOrder(Connection c, UUID partId, List<UUID> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE chapter SET display_order = ?, updated_at = ?
                WHERE id = ? AND part_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Instant now = Instant.now();
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(1, i);
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setObject(3, ids.get(i));
                ps.setObject(4, partId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Assigns display_order 0..n-1 to the given codex category-chapter IDs.
     * The codex_id guard prevents accidental updates to chapters in a different codex.
     */
    public void reorderInCodex(UUID codexId, List<UUID> ids) throws SQLException {
        String sql = """
                UPDATE chapter SET display_order = ?, updated_at = ?
                WHERE id = ? AND codex_id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                for (int i = 0; i < ids.size(); i++) {
                    ps.setInt(1, i);
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, ids.get(i));
                    ps.setObject(4, codexId);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Moves a chapter between containers and renumbers both the source and the
     * target sibling list in one transaction.
     *
     * <p>Either container may be a part or the book outline itself, and the two
     * are renumbered differently: a part's chapters are a plain chapter list,
     * while the book outline spans the {@code part} and {@code chapter} tables
     * together. The caller therefore names each container explicitly and sends
     * typed items rather than bare UUIDs — a bare ID list cannot say whether a
     * given entry is a part row or a chapter row, and the writer has to know
     * which table to update.
     *
     * @param chapterId    the chapter being moved
     * @param sourcePartId the part it came from, or null if it was a direct-book chapter
     * @param sourceItems  the source container's contents AFTER removal
     * @param targetPartId the part it is going to, or null to make it a direct-book chapter
     * @param targetItems  the target container's contents AFTER insertion (includes chapterId)
     */
    public void moveChapter(UUID chapterId,
            UUID sourcePartId, List<OutlineRef> sourceItems,
            UUID targetPartId, List<OutlineRef> targetItems) throws SQLException {

        Instant now = Instant.now();
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                UUID bookId = bookIdOf(conn, chapterId);
                if (bookId == null) {
                    throw new SQLException("moveChapter: chapter " + chapterId + " has no book");
                }

                // 1. Reparent (null = promote to direct-book chapter).
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE chapter SET part_id = ?, updated_at = ? WHERE id = ?")) {
                    ps.setObject(1, targetPartId); // setObject handles a null UUID correctly
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, chapterId);
                    ps.executeUpdate();
                }

                // 2. Close the gap in the source container.
                renumber(conn, bookId, sourcePartId, sourceItems);

                // 3. Renumber the target container, which now holds the chapter.
                renumber(conn, bookId, targetPartId, targetItems);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Renumbers one container: a part's chapter list, or the book outline. */
    private static void renumber(Connection conn, UUID bookId, UUID partId, List<OutlineRef> items)
            throws SQLException {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (partId != null) {
            List<UUID> ids = new ArrayList<>(items.size());
            for (OutlineRef item : items) {
                if (item != null && item.id() != null) {
                    ids.add(item.id());
                }
            }
            applyPartOrder(conn, partId, ids);
        } else {
            BookOutline.applyOrder(conn, bookId, items);
        }
    }

    private static UUID bookIdOf(Connection conn, UUID chapterId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT book_id FROM chapter WHERE id = ?")) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject("book_id", UUID.class) : null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Position allocation
    // -------------------------------------------------------------------------

    /**
     * The next free position at the end of the book's outline, past every part
     * and every direct-book chapter.
     *
     * <p>Exposed for {@code TrashService}, which appends a restored direct-book
     * chapter to the end of its container. Taking {@code max(direct chapter
     * order) + 1} there — as it did before V40 — would now land on top of a part.
     */
    public int nextOutlinePosition(UUID bookId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return BookOutline.nextPosition(c, bookId);
        }
    }

    /** Scopes display_order to chapters within a specific part. */
    private static int nextDisplayOrderInPart(Connection c, UUID partId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM chapter WHERE part_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Opens a slot before/after a sibling chapter inside a part, pushing the
     * chapters at or after it down by one.
     */
    private static int openSlotInPart(Connection c, UUID partId, UUID anchorId, boolean before)
            throws SQLException {
        Integer anchorPos = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT display_order FROM chapter WHERE id = ? AND part_id = ?")) {
            ps.setObject(1, anchorId);
            ps.setObject(2, partId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    anchorPos = rs.getInt(1);
                }
            }
        }
        if (anchorPos == null) {
            return nextDisplayOrderInPart(c, partId);
        }

        int target = before ? anchorPos : anchorPos + 1;
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE chapter SET display_order = display_order + 1, updated_at = ?
                WHERE part_id = ? AND display_order >= ?
                """)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setObject(2, partId);
            ps.setInt(3, target);
            ps.executeUpdate();
        }
        return target;
    }

    /** Scopes display_order to category chapters within a specific codex. */
    private static int nextDisplayOrderInCodex(Connection c, UUID codexId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM chapter WHERE codex_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, codexId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
