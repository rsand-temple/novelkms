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

import com.richardsand.novelkms.model.Chapter;

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
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .chapterNumber(rs.getInt("chapter_number"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Chapter> findByBookId(UUID bookId) throws SQLException {
        String sql = "WITH ordered AS ( " +
                "  SELECT c.id, " +
                "    ROW_NUMBER() OVER ( " +
                "      ORDER BY " +
                "        CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END, " +
                "        p.display_order, " +
                "        c.display_order " +
                "    ) AS chapter_number " +
                "  FROM chapter c " +
                "  LEFT JOIN part p ON c.part_id = p.id " +
                "  WHERE c.book_id = ? " +
                ") " +
                "SELECT c.id, c.book_id, c.part_id, c.title, c.subtitle, c.notes, " +
                "       c.display_order, c.created_at, c.updated_at, " +
                "       o.chapter_number " +
                "FROM chapter c " +
                "JOIN ordered o ON c.id = o.id " +
                "LEFT JOIN part p ON c.part_id = p.id " +
                "WHERE c.book_id = ? AND c.part_id IS NULL " +
                "ORDER BY o.chapter_number";

        List<Chapter>     result = new ArrayList<>();
        Connection        conn   = ds.getConnection();
        PreparedStatement ps     = conn.prepareStatement(sql);
        ps.setObject(1, bookId); // CTE
        ps.setObject(2, bookId); // outer WHERE
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    public List<Chapter> findByPartId(UUID partId) throws SQLException {
        String sql = "WITH ordered AS ( " +
                "  SELECT c.id, " +
                "    ROW_NUMBER() OVER ( " +
                "      ORDER BY " +
                "        CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END, " +
                "        p.display_order, " +
                "        c.display_order " +
                "    ) AS chapter_number " +
                "  FROM chapter c " +
                "  LEFT JOIN part p ON c.part_id = p.id " +
                "  WHERE c.book_id = (SELECT book_id FROM part WHERE id = ?) " +
                ") " +
                "SELECT c.id, c.book_id, c.part_id, c.title, c.subtitle, c.notes, " +
                "       c.display_order, c.created_at, c.updated_at, " +
                "       o.chapter_number " +
                "FROM chapter c " +
                "JOIN ordered o ON c.id = o.id " +
                "WHERE c.part_id = ? " +
                "ORDER BY o.chapter_number";

        List<Chapter>     result = new ArrayList<>();
        Connection        conn   = ds.getConnection();
        PreparedStatement ps     = conn.prepareStatement(sql);
        ps.setObject(1, partId); // CTE subquery
        ps.setObject(2, partId); // outer WHERE
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    public Optional<Chapter> findById(UUID id) throws SQLException {
        String sql = "WITH ordered AS ( " +
                "  SELECT c.id, " +
                "    ROW_NUMBER() OVER ( " +
                "      ORDER BY " +
                "        CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END, " +
                "        p.display_order, " +
                "        c.display_order " +
                "    ) AS chapter_number " +
                "  FROM chapter c " +
                "  LEFT JOIN part p ON c.part_id = p.id " +
                "  WHERE c.book_id = (SELECT book_id FROM chapter WHERE id = ?) " +
                ") " +
                "SELECT c.id, c.book_id, c.part_id, c.title, c.subtitle, c.notes, " +
                "       c.display_order, c.created_at, c.updated_at, " +
                "       o.chapter_number " +
                "FROM chapter c " +
                "JOIN ordered o ON c.id = o.id " +
                "WHERE c.id = ?";

        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id); // CTE subquery
            ps.setObject(2, id); // outer WHERE
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    public Chapter create(UUID bookId, UUID partId, String title, String subtitle, String notes) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        // Scope display_order to the immediate parent: part (if set) or book (direct).
        int    displayOrder = (partId != null)
                ? nextDisplayOrderInPart(partId)
                : nextDisplayOrderInBook(bookId);
        String sql          = """
                INSERT INTO chapter (id, book_id, part_id, title, subtitle, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
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
        return Chapter.builder()
                .id(id).bookId(bookId).partId(partId).title(title).subtitle(subtitle)
                .displayOrder(displayOrder).notes(notes)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Chapter> update(UUID id, String title, String subtitle, String notes) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE chapter SET title = ?, subtitle = ?, notes = ?, updated_at = ?
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
    // -------------------------------------------------------------------------

    /**
     * Assigns display_order 0..n-1 to the given chapter IDs in the order supplied.
     * The book_id guard in the WHERE clause prevents updates to chapters belonging
     * to a different book if a stale ID list is passed in.
     * Runs as a single batched transaction.
     */
    public void reorderInBook(UUID bookId, List<UUID> ids) throws SQLException {
        String sql = """
                UPDATE chapter SET display_order = ?, updated_at = ?
                WHERE id = ? AND book_id = ?
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
                    ps.setObject(4, bookId);
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
     * Assigns display_order 0..n-1 to the given chapter IDs within a part.
     * The part_id guard prevents accidental updates to chapters in a different part.
     */
    public void reorderInPart(UUID partId, List<UUID> ids) throws SQLException {
        String sql = """
                UPDATE chapter SET display_order = ?, updated_at = ?
                WHERE id = ? AND part_id = ?
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
                    ps.setObject(4, partId);
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
     * Moves a chapter to a new parent container and renumbers both the source
     * and target sibling lists in a single transaction.
     *
     * @param chapterId the chapter being moved
     * @param newPartId null = book-direct, non-null = inside this part
     * @param sourceIds ordered IDs of the source container AFTER removal
     * @param targetIds ordered IDs of the target container AFTER insertion (includes chapterId)
     */
    public void moveChapter(UUID chapterId, UUID newPartId,
            List<UUID> sourceIds, List<UUID> targetIds) throws SQLException {
        Instant now = Instant.now();
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update part_id on the moved chapter (null = promote to book-direct)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE chapter SET part_id = ?, updated_at = ? WHERE id = ?")) {
                    ps.setObject(1, newPartId); // setObject handles null UUID correctly
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, chapterId);
                    ps.executeUpdate();
                }

                // 2. Renumber source container (closes the gap left by removal)
                if (!sourceIds.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE chapter SET display_order = ?, updated_at = ? WHERE id = ?")) {
                        for (int i = 0; i < sourceIds.size(); i++) {
                            ps.setInt(1, i);
                            ps.setTimestamp(2, Timestamp.from(now));
                            ps.setObject(3, sourceIds.get(i));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // 3. Renumber target container (includes the moved chapter)
                if (!targetIds.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE chapter SET display_order = ?, updated_at = ? WHERE id = ?")) {
                        for (int i = 0; i < targetIds.size(); i++) {
                            ps.setInt(1, i);
                            ps.setTimestamp(2, Timestamp.from(now));
                            ps.setObject(3, targetIds.get(i));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("moveChapter transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Scopes display_order to direct-book chapters only (part_id IS NULL).
     * Used when creating a chapter directly under a book.
     */
    private int nextDisplayOrderInBook(UUID bookId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM chapter WHERE book_id = ? AND part_id IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Scopes display_order to chapters within a specific part.
     * Used when creating a chapter inside a part.
     */
    private int nextDisplayOrderInPart(UUID partId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM chapter WHERE part_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}