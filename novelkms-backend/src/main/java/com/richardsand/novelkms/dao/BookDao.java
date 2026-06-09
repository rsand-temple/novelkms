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
                .pageLayoutEnabled(rs.getBoolean("page_layout_enabled"))
                .pageSizePreset(rs.getString("page_size_preset"))
                .pageWidthIn(rs.getObject("page_width_in", Double.class))
                .pageHeightIn(rs.getObject("page_height_in", Double.class))
                .pageMarginTopIn(rs.getObject("page_margin_top_in", Double.class))
                .pageMarginBottomIn(rs.getObject("page_margin_bottom_in", Double.class))
                .pageMarginInnerIn(rs.getObject("page_margin_inner_in", Double.class))
                .pageMarginOuterIn(rs.getObject("page_margin_outer_in", Double.class))
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
                .pageLayoutEnabled(false).pageSizePreset("LETTER")
                .pageMarginTopIn(1.0).pageMarginBottomIn(1.0)
                .pageMarginInnerIn(1.25).pageMarginOuterIn(1.0)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Book> update(UUID id, String title, String subtitle, String shortTitle, String notes,
            boolean pageLayoutEnabled, String pageSizePreset,
            Double pageWidthIn, Double pageHeightIn,
            Double pageMarginTopIn, Double pageMarginBottomIn,
            Double pageMarginInnerIn, Double pageMarginOuterIn) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE book
                SET title = ?, subtitle = ?, short_title = ?, notes = ?,
                    page_layout_enabled = ?, page_size_preset = ?,
                    page_width_in = ?, page_height_in = ?,
                    page_margin_top_in = ?, page_margin_bottom_in = ?,
                    page_margin_inner_in = ?, page_margin_outer_in = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, subtitle);
            ps.setString(3, shortTitle);
            ps.setString(4, notes);
            ps.setBoolean(5, pageLayoutEnabled);
            ps.setString(6, pageSizePreset != null ? pageSizePreset : "LETTER");
            ps.setObject(7, pageWidthIn);   // null-safe via setObject
            ps.setObject(8, pageHeightIn);
            ps.setObject(9, pageMarginTopIn);
            ps.setObject(10, pageMarginBottomIn);
            ps.setObject(11, pageMarginInnerIn);
            ps.setObject(12, pageMarginOuterIn);
            ps.setTimestamp(13, Timestamp.from(now));
            ps.setObject(14, id);
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
