package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.BookSummary;

/**
 * Storage for the one-per-book summary. {@code book_id} is unique;
 * {@link #upsertGenerated} overwrites in place. The row cascades away on a hard
 * book purge; a trashed book keeps it (and it reappears on restore), consistent
 * with the chapter-level artifacts.
 */
public class BookSummaryDao {

    private final BasicDataSource ds;

    public BookSummaryDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private BookSummary map(ResultSet rs) throws SQLException {
        return BookSummary.builder()
                .id(rs.getObject("id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .content(rs.getString("content"))
                .wordCount(rs.getInt("word_count"))
                .source(rs.getString("source"))
                .promptVersion(rs.getString("prompt_version"))
                .model(rs.getString("model"))
                .generatedAt(instant(rs, "generated_at"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }

    public Optional<BookSummary> findByBook(UUID bookId) throws SQLException {
        String sql = "SELECT * FROM book_summary WHERE book_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserts or overwrites the book's AI-generated summary, refreshing
     * {@code generatedAt}. Sets {@code source = 'AI'}.
     */
    public void upsertGenerated(UUID bookId, String content, int wordCount,
            String promptVersion, String model) throws SQLException {
        Instant now = Instant.now();
        if (findByBook(bookId).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE book_summary SET content=?, word_count=?, source='AI', prompt_version=?,"
                                    + " model=?, generated_at=?, updated_at=? WHERE book_id=?")) {
                p.setString(1, content);
                p.setInt(2, wordCount);
                p.setString(3, promptVersion);
                p.setString(4, model);
                p.setTimestamp(5, Timestamp.from(now));
                p.setTimestamp(6, Timestamp.from(now));
                p.setObject(7, bookId);
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO book_summary(id, book_id, content, word_count, source, prompt_version,"
                                    + " model, generated_at, created_at, updated_at)"
                                    + " VALUES (?,?,?,?,'AI',?,?,?,?,?)")) {
                p.setObject(1, UUID.randomUUID());
                p.setObject(2, bookId);
                p.setString(3, content);
                p.setInt(4, wordCount);
                p.setString(5, promptVersion);
                p.setString(6, model);
                p.setTimestamp(7, Timestamp.from(now));
                p.setTimestamp(8, Timestamp.from(now));
                p.setTimestamp(9, Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    /**
     * Replaces an existing summary's text with author-edited content, marking it
     * {@code source = 'EDITED'} and refreshing {@code generatedAt}. Returns false
     * if the book has no summary to edit.
     */
    public boolean updateEdited(UUID bookId, String content, int wordCount) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE book_summary SET content=?, word_count=?, source='EDITED', generated_at=?,"
                                + " updated_at=? WHERE book_id=?")) {
            p.setString(1, content);
            p.setInt(2, wordCount);
            p.setTimestamp(3, Timestamp.from(now));
            p.setTimestamp(4, Timestamp.from(now));
            p.setObject(5, bookId);
            return p.executeUpdate() > 0;
        }
    }

    public boolean delete(UUID bookId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM book_summary WHERE book_id=?")) {
            p.setObject(1, bookId);
            return p.executeUpdate() > 0;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
