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

import com.richardsand.novelkms.model.Book;

public class BookDao {

    private final BasicDataSource ds;

    public BookDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Book map(ResultSet rs) throws SQLException {
        return Book.builder()
                .id(rs.getObject("id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .shortTitle(rs.getString("short_title"))
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Book> findByProjectId(UUID projectId) throws SQLException {
        String     sql    = "SELECT * FROM book WHERE project_id = ? ORDER BY display_order, title";
        List<Book> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Optional<Book> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM book WHERE id = ?";
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

    public Book create(UUID projectId, String title, String subtitle, String shortTitle, String notes) throws SQLException {
        UUID    id           = UUID.randomUUID();
        Instant now          = Instant.now();
        int     displayOrder = nextDisplayOrder(projectId);
        String  sql          = """
                INSERT INTO book (id, project_id, title, subtitle, short_title, display_order, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setString(3, title);
            ps.setString(4, subtitle);
            ps.setString(5, shortTitle);
            ps.setInt(6, displayOrder);
            ps.setString(7, notes);
            ps.setTimestamp(8, Timestamp.from(now));
            ps.setTimestamp(9, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Book.builder()
                .id(id).projectId(projectId).title(title).subtitle(subtitle).shortTitle(shortTitle)
                .displayOrder(displayOrder).notes(notes)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Book> update(UUID id, String title, String subtitle, String shortTitle, String notes) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE book SET title = ?, subtitle = ?, short_title = ?, notes = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, subtitle);
            ps.setString(3, shortTitle);
            ps.setString(4, notes);
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
        String sql = "DELETE FROM book WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

    /** Appends to the end of the current list within the parent project. */
    private int nextDisplayOrder(UUID projectId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM book WHERE project_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
