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

    /**
     * Holds the raw bytes and MIME type of a cover image.
     * Only materialized by getCoverImage(); never included in normal book rows.
     */
    public record CoverImage(byte[] data, String mimeType) {
    }

    public BookDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Shared SELECT fragment
    //
    // cover_image is intentionally excluded — the column may hold megabytes of
    // binary data. has_cover_image is a lightweight CASE expression that tells
    // the caller whether an image exists without loading the bytes.
    // -------------------------------------------------------------------------

    private static final String SELECT_COLUMNS = """
            SELECT id, project_id, title, subtitle, short_title, display_order, notes,
                   imported_from, imported_at,
                   created_at, updated_at,
                   CASE WHEN cover_image IS NOT NULL THEN TRUE ELSE FALSE END AS has_cover_image
            FROM book
            """;

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Book map(ResultSet rs) throws SQLException {
        Timestamp importedAtTs = rs.getTimestamp("imported_at");
        return Book.builder()
                .id(rs.getObject("id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .shortTitle(rs.getString("short_title"))
                .displayOrder(rs.getInt("display_order"))
                .notes(rs.getString("notes"))
                .hasCoverImage(rs.getBoolean("has_cover_image"))
                .importedFrom(rs.getString("imported_from"))
                .importedAt(importedAtTs != null ? importedAtTs.toInstant() : null)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Book> findByProjectId(UUID projectId) throws SQLException {
        String     sql    = SELECT_COLUMNS + "WHERE project_id = ? AND deleted_at IS NULL ORDER BY display_order, title";
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
        String sql = SELECT_COLUMNS + "WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cover image
    // -------------------------------------------------------------------------

    public Optional<CoverImage> getCoverImage(UUID id) throws SQLException {
        String sql = "SELECT cover_image, cover_image_mime_type FROM book WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();
                byte[] bytes = rs.getBytes("cover_image");
                if (bytes == null)
                    return Optional.empty();
                return Optional.of(new CoverImage(bytes, rs.getString("cover_image_mime_type")));
            }
        }
    }

    public boolean setCoverImage(UUID id, byte[] data, String mimeType) throws SQLException {
        String sql = """
                UPDATE book
                SET cover_image = ?, cover_image_mime_type = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBytes(1, data);
            ps.setString(2, mimeType);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteCoverImage(UUID id) throws SQLException {
        String sql = """
                UPDATE book
                SET cover_image = NULL, cover_image_mime_type = NULL, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setObject(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Import metadata
    // -------------------------------------------------------------------------

    public void setImportMetadata(UUID id, String importedFrom, Instant importedAt) throws SQLException {
        String sql = """
                UPDATE book
                SET imported_from = ?, imported_at = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, importedFrom);
            ps.setTimestamp(2, Timestamp.from(importedAt));
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, id);
            ps.executeUpdate();
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
                .hasCoverImage(false)
                .importedFrom(null).importedAt(null)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Book> update(UUID id, String title, String subtitle, String shortTitle, String notes)
            throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE book
                SET title = ?, subtitle = ?, short_title = ?, notes = ?,
                    updated_at = ?
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
    // Word count
    // -------------------------------------------------------------------------

    /**
     * Returns the total manuscript word count for a single book, including:
     * - scene.word_count for every scene in the book
     * - words in every chapter title/subtitle (blank title = 2 words for "Chapter N")
     * - words in every part title/subtitle (blank title = 2 words for "Part I")
     *
     * Used by GET /api/books/{id}/word-count (status bar when book is selected)
     * and by ExportService for the WORDS token in DOCX export.
     */
    public int getTotalWordCount(UUID bookId) throws SQLException {
        int total = 0;

        // 1. Scene content
        String sceneSql = """
                SELECT COALESCE(SUM(s.word_count), 0)
                FROM scene s
                JOIN chapter ch ON ch.id = s.chapter_id
                WHERE ch.book_id = ? AND ch.deleted_at IS NULL AND s.deleted_at IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sceneSql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    total += rs.getInt(1);
            }
        }

        // 2. Chapter heading words — blank title counts as 2 ("Chapter N")
        String chapterSql = "SELECT title, subtitle FROM chapter WHERE book_id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(chapterSql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("title");
                    String s = rs.getString("subtitle");
                    total += (t == null || t.isBlank()) ? 2 : countPlainTextWords(t);
                    total += countPlainTextWords(s);
                }
            }
        }

        // 3. Part heading words — blank title counts as 2 ("Part I")
        String partSql = "SELECT title, subtitle FROM part WHERE book_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(partSql)) {
            ps.setObject(1, bookId);
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

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static int countPlainTextWords(String text) {
        if (text == null || text.isBlank())
            return 0;
        int     count  = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return count;
    }
}