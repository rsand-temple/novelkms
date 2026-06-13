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
        // ROW_NUMBER() over display_order gives the 1-based part number within
        // the book, matching the order the nav tree renders them.
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
        // Scope ROW_NUMBER() to the part's own book via scalar subquery,
        // matching the approach used in ChapterDao.findById().
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

    public Part create(UUID bookId, String title, String subtitle, String notes) throws SQLException {
        UUID    id           = UUID.randomUUID();
        Instant now          = Instant.now();
        int     displayOrder = nextDisplayOrder(bookId);
        String  sql          = """
                INSERT INTO part (id, book_id, title, subtitle, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
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
        // displayOrder is 0-based count of existing parts, so partNumber = displayOrder + 1
        return Part.builder()
                .id(id).bookId(bookId).title(title).subtitle(subtitle)
                .displayOrder(displayOrder).notes(notes)
                .partNumber(displayOrder + 1)
                .createdAt(now).updatedAt(now)
                .build();
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
    // -------------------------------------------------------------------------

    public void reorderInBook(UUID bookId, List<UUID> ids) throws SQLException {
        String sql = """
                UPDATE part SET display_order = ?, updated_at = ?
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

    private int nextDisplayOrder(UUID bookId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM part WHERE book_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}