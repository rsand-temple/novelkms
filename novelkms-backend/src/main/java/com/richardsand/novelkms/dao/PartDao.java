package com.richardsand.novelkms.dao;

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

import com.richardsand.novelkms.dao.book.BookOutline;
import com.richardsand.novelkms.model.Part;

public class PartDao {

    private final BasicDataSource ds;

    public PartDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Part map(ResultSet rs) throws SQLException {
        return Part.builder()
                .id(rs.getObject("id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .partNumber(rs.getInt("part_number"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Part> findByBookId(UUID bookId) throws SQLException {
        String     sql    = """
                WITH numbered AS (
                    SELECT *,
                           ROW_NUMBER() OVER (ORDER BY display_order) AS part_number
                    FROM part
                    WHERE book_id = ?
                )
                SELECT * FROM numbered ORDER BY display_order
                """;
        List<Part> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Optional<Part> findById(UUID id) throws SQLException {
        String sql = """
                WITH numbered AS (
                    SELECT *,
                           ROW_NUMBER() OVER (PARTITION BY book_id ORDER BY display_order) AS part_number
                    FROM part
                    WHERE book_id = (SELECT book_id FROM part WHERE id = ?)
                )
                SELECT * FROM numbered WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Creates a part at the end of the book outline — past every existing part
     * <em>and</em> every direct-book chapter, since V40 gave them one shared
     * display_order sequence.
     */
    public Part create(UUID bookId, String title, String subtitle, String notes) throws SQLException {
        return insert(bookId, title, subtitle, notes, null, false);
    }

    /**
     * Creates a part immediately before or after an existing outline item. The
     * anchor may be another part or a direct-book chapter — a part can now be
     * inserted after a prologue just as readily as after another part.
     *
     * <p>An unresolvable anchor appends rather than failing, matching
     * {@code ChapterDao}.
     */
    public Part createRelativeTo(UUID bookId, String title, String subtitle, String notes,
            UUID anchorId, boolean before) throws SQLException {
        return insert(bookId, title, subtitle, notes, anchorId, before);
    }

    private Part insert(UUID bookId, String title, String subtitle, String notes,
            UUID anchorId, boolean before) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String  sql = """
                INSERT INTO part (id, book_id, title, subtitle, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int displayOrder;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Opening a slot shifts existing outline items in BOTH tables, so
                // the shift and the insert have to land together or not at all.
                displayOrder = anchorId != null
                        ? BookOutline.openSlot(c, bookId, anchorId, before)
                        : BookOutline.nextPosition(c, bookId);

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, id);
                    ps.setObject(2, bookId);
                    ps.setString(3, title);
                    ps.setString(4, subtitle);
                    ps.setInt(5, displayOrder);
                    ps.setString(6, notes);
                    ps.setTimestamp(7, Timestamp.from(now));
                    ps.setTimestamp(8, Timestamp.from(now));
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

        // part_number is the part's rank among parts (Part I, II, III...), which is
        // NOT its outline position now that chapters share the sequence — a part at
        // outline position 4 may well be Part I. Re-read so the caller gets the
        // computed number rather than an arithmetic guess.
        return findById(id).orElseGet(() -> Part.builder()
                .id(id).bookId(bookId).title(title).subtitle(subtitle)
                .displayOrder(displayOrder).notes(notes)
                .createdAt(now).updatedAt(now)
                .build());
    }

    public Optional<Part> update(UUID id, String title, String subtitle, String notes) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE part SET title = ?, subtitle = ?, notes = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, subtitle);
            ps.setString(3, notes);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setObject(5, id);
            int rows = ps.executeUpdate();
            if (rows == 0)
                return Optional.empty();
        }
        return findById(id);
    }

    public boolean delete(UUID id) throws SQLException {
        String sql = "DELETE FROM part WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Ordering
    //
    // Parts have no ordering method of their own. They share one display_order
    // sequence with the book's direct chapters (V40), so reordering them in
    // isolation would collide with the chapters interleaved among them. All
    // book-level ordering goes through BookOutline / BookOutlineDao.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Word count
    // -------------------------------------------------------------------------

    /**
     * Returns the total word count for a single part, including:
     *   - the part's own title/subtitle words (blank title = 2 for "Part I")
     *   - scene.word_count for every scene in every chapter in this part
     *   - words in every chapter title/subtitle in this part (blank = 2 for "Chapter N")
     *
     * Used by GET /api/parts/{id}/word-count (status bar when a part is selected).
     */
    public int getTotalWordCount(UUID partId) throws SQLException {
        int total = 0;

        // 1. Part's own heading words
        String partSql = "SELECT title, subtitle FROM part WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(partSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String t = rs.getString("title");
                    String s = rs.getString("subtitle");
                    total += (t == null || t.isBlank()) ? 2 : countPlainTextWords(t);
                    total += countPlainTextWords(s);
                }
            }
        }

        // 2. Scene content for chapters in this part
        String sceneSql = """
                SELECT COALESCE(SUM(s.word_count), 0)
                FROM scene s
                JOIN chapter ch ON ch.id = s.chapter_id
                WHERE ch.part_id = ? AND ch.deleted_at IS NULL AND s.deleted_at IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sceneSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getInt(1);
            }
        }

        // 3. Chapter heading words for chapters in this part
        String chapterSql = "SELECT title, subtitle FROM chapter WHERE part_id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(chapterSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("title");
                    String s = rs.getString("subtitle");
                    total += (t == null || t.isBlank()) ? 2 : countPlainTextWords(t);
                    total += countPlainTextWords(s);
                }
            }
        }

        return total;
    }

    /**
     * Returns the total paragraph count for a single part, mirroring
     * {@link #getTotalWordCount(UUID)}: one "paragraph" for the part's own
     * title (plus one more for a non-blank subtitle), scene.paragraph_count
     * for every scene in every chapter in this part, and one per chapter
     * title/non-blank subtitle in this part.
     *
     * Feeds the estimated-page-count figure alongside word count and page
     * size (see utils/pageEstimate.js on the frontend); a rough count is fine
     * here, not an exact one.
     */
    public int getTotalParagraphCount(UUID partId) throws SQLException {
        int total = 0;

        String partSql = "SELECT title, subtitle FROM part WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(partSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total += 1;
                    String s = rs.getString("subtitle");
                    if (s != null && !s.isBlank())
                        total += 1;
                }
            }
        }

        String sceneSql = """
                SELECT COALESCE(SUM(s.paragraph_count), 0)
                FROM scene s
                JOIN chapter ch ON ch.id = s.chapter_id
                WHERE ch.part_id = ? AND ch.deleted_at IS NULL AND s.deleted_at IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sceneSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getInt(1);
            }
        }

        String chapterSql = "SELECT title, subtitle FROM chapter WHERE part_id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(chapterSql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total += 1;
                    String s = rs.getString("subtitle");
                    if (s != null && !s.isBlank())
                        total += 1;
                }
            }
        }

        return total;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------


    private static int countPlainTextWords(String text) {
        if (text == null || text.isBlank()) return 0;
        int     count  = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                if (!inWord) { count++; inWord = true; }
            } else {
                inWord = false;
            }
        }
        return count;
    }
}