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
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Chapter> findByBookId(UUID bookId) throws SQLException {
        // Returns only chapters that sit directly under the book (part_id IS NULL).
        // Chapters inside a part are fetched via findByPartId.
        String        sql    = "SELECT * FROM chapter WHERE book_id = ? AND part_id IS NULL ORDER BY display_order, title";
        List<Chapter> result = new ArrayList<>();
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

    public List<Chapter> findByPartId(UUID partId) throws SQLException {
        String        sql    = "SELECT * FROM chapter WHERE part_id = ? ORDER BY display_order, title";
        List<Chapter> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Optional<Chapter> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM chapter WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    public Chapter create(UUID bookId, UUID partId, String title, String notes) throws SQLException {
        UUID    id           = UUID.randomUUID();
        Instant now          = Instant.now();
        // Scope display_order to the immediate parent: part (if set) or book (direct).
        int     displayOrder = (partId != null)
                ? nextDisplayOrderInPart(partId)
                : nextDisplayOrderInBook(bookId);
        String  sql          = """
                INSERT INTO chapter (id, book_id, part_id, title, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, bookId);
            ps.setObject(3, partId); // null-safe: setObject handles null correctly
            ps.setString(4, title);
            ps.setInt(5, displayOrder);
            ps.setString(6, notes);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.setTimestamp(8, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Chapter.builder()
                .id(id).bookId(bookId).partId(partId).title(title)
                .displayOrder(displayOrder).notes(notes)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Chapter> update(UUID id, String title, String notes) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE chapter SET title = ?, notes = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, notes);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setObject(4, id);
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
