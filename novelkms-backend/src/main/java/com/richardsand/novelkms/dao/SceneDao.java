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

import com.richardsand.novelkms.model.Scene;

public class SceneDao {

    private final BasicDataSource ds;

    public SceneDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Scene map(ResultSet rs) throws SQLException {
        return Scene.builder()
                .id(rs.getObject("id", UUID.class))
                .chapterId(rs.getObject("chapter_id", UUID.class))
                .title(rs.getString("title"))
                .displayOrder(rs.getInt("display_order"))
                .content(rs.getString("content"))
                .wordCount(rs.getInt("word_count"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Scene> findByChapterId(UUID chapterId) throws SQLException {
        String      sql    = "SELECT * FROM scene WHERE chapter_id = ? ORDER BY display_order, title";
        List<Scene> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Optional<Scene> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM scene WHERE id = ?";
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

    public Scene create(UUID chapterId, String title, String notes) throws SQLException {
        UUID    id           = UUID.randomUUID();
        Instant now          = Instant.now();
        int     displayOrder = nextDisplayOrder(chapterId);
        // Default title for programmatically-created scenes (e.g. scene breaks)
        if (title == null || title.isBlank()) {
            title = "New Scene [" + id.toString().substring(0, 4) + "]";
        }
        String  sql          = """
                INSERT INTO scene (id, chapter_id, title, display_order, content, word_count, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, 0, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, chapterId);
            ps.setString(3, title);
            ps.setInt(4, displayOrder);
            ps.setString(5, notes);
            ps.setTimestamp(6, Timestamp.from(now));
            ps.setTimestamp(7, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Scene.builder()
                .id(id).chapterId(chapterId).title(title)
                .displayOrder(displayOrder).wordCount(0).notes(notes)
                .createdAt(now).updatedAt(now)
                .build();
    }

    /**
     * Saves scene content and recalculates word count.
     * Word count is a simple whitespace-split estimate; the frontend may
     * supply a more accurate count derived from the TipTap document model.
     */
    public Optional<Scene> saveContent(UUID id, String content, int wordCount) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE scene SET content = ?, word_count = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setInt(2, wordCount);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setObject(4, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    public Optional<Scene> update(UUID id, String title, String notes) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE scene SET title = ?, notes = ?, updated_at = ?
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
        String sql = "DELETE FROM scene WHERE id = ?";
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
     * Assigns display_order 0..n-1 to the given scene IDs in the order supplied.
     * The chapter_id guard in the WHERE clause prevents updates to scenes belonging
     * to a different chapter if a stale ID list is passed in.
     * Runs as a single batched transaction.
     */
    public void reorderInChapter(UUID chapterId, List<UUID> ids) throws SQLException {
        String sql = """
                UPDATE scene SET display_order = ?, updated_at = ?
                WHERE id = ? AND chapter_id = ?
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
                    ps.setObject(4, chapterId);
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

    private int nextDisplayOrder(UUID chapterId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM scene WHERE chapter_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}