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

import com.richardsand.novelkms.model.BookSummary;

/**
 * Storage for book summaries. Since V36 a book holds at most one summary
 * <em>per provider</em> ({@code book_summary} is unique on
 * {@code (book_id, provider)}); every operation keys on {@code (bookId, provider)}
 * and {@link #upsertGenerated} overwrites that provider's summary in place. The
 * rows cascade away on a hard book purge; a trashed book keeps them (and they
 * reappear on restore), consistent with the chapter-level artifacts.
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
                .provider(rs.getString("provider"))
                .content(rs.getString("content"))
                .wordCount(rs.getInt("word_count"))
                .source(rs.getString("source"))
                .promptVersion(rs.getString("prompt_version"))
                .model(rs.getString("model"))
                .userGuidance(rs.getString("user_guidance"))
                .generatedAt(instant(rs, "generated_at"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }

    /** Returns the book's summary for exactly the given provider, if any. */
    public Optional<BookSummary> findByBook(UUID bookId, String provider) throws SQLException {
        String sql = "SELECT * FROM book_summary WHERE book_id = ? AND provider = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId);
            p.setString(2, provider);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the book's preferred summary: the {@code preferredProvider} variant
     * when present, otherwise the book's most-recently-updated summary of any
     * provider. Returns empty when the book has no summary at all. A null/blank
     * {@code preferredProvider} simply yields the most-recent variant.
     */
    public Optional<BookSummary> findPreferred(UUID bookId, String preferredProvider) throws SQLException {
        String sql = "SELECT * FROM book_summary WHERE book_id = ? "
                + "ORDER BY CASE WHEN provider = ? THEN 0 ELSE 1 END, updated_at DESC";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId);
            p.setString(2, preferredProvider);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Returns every provider variant of the book's summary, newest first. */
    public List<BookSummary> findAllByBook(UUID bookId) throws SQLException {
        String sql = "SELECT * FROM book_summary WHERE book_id = ? "
                + "ORDER BY updated_at DESC, provider ASC";
        List<BookSummary> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /**
     * Inserts or overwrites the {@code (book, provider)} AI-generated summary,
     * refreshing {@code generatedAt}. Sets {@code source = 'AI'}.
     * {@code userGuidance} is the optional one-time author note supplied for this
     * generation (null when none).
     */
    public void upsertGenerated(UUID bookId, String provider, String content, int wordCount,
            String promptVersion, String model, String userGuidance) throws SQLException {
        Instant now = Instant.now();
        if (findByBook(bookId, provider).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE book_summary SET content=?, word_count=?, source='AI', prompt_version=?,"
                                    + " model=?, user_guidance=?, generated_at=?, updated_at=?"
                                    + " WHERE book_id=? AND provider=?")) {
                p.setString(1, content);
                p.setInt(2, wordCount);
                p.setString(3, promptVersion);
                p.setString(4, model);
                p.setString(5, userGuidance);
                p.setTimestamp(6, Timestamp.from(now));
                p.setTimestamp(7, Timestamp.from(now));
                p.setObject(8, bookId);
                p.setString(9, provider);
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO book_summary(id, book_id, provider, content, word_count, source, prompt_version,"
                                    + " model, user_guidance, generated_at, created_at, updated_at)"
                                    + " VALUES (?,?,?,?,?,'AI',?,?,?,?,?,?)")) {
                p.setObject(1, UUID.randomUUID());
                p.setObject(2, bookId);
                p.setString(3, provider);
                p.setString(4, content);
                p.setInt(5, wordCount);
                p.setString(6, promptVersion);
                p.setString(7, model);
                p.setString(8, userGuidance);
                p.setTimestamp(9, Timestamp.from(now));
                p.setTimestamp(10, Timestamp.from(now));
                p.setTimestamp(11, Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    /**
     * Replaces an existing summary's text with author-edited content for the given
     * provider, marking it {@code source = 'EDITED'} and refreshing
     * {@code generatedAt}. Returns false if the book has no summary for that
     * provider to edit.
     */
    public boolean updateEdited(UUID bookId, String provider, String content, int wordCount) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE book_summary SET content=?, word_count=?, source='EDITED', generated_at=?,"
                                + " updated_at=? WHERE book_id=? AND provider=?")) {
            p.setString(1, content);
            p.setInt(2, wordCount);
            p.setTimestamp(3, Timestamp.from(now));
            p.setTimestamp(4, Timestamp.from(now));
            p.setObject(5, bookId);
            p.setString(6, provider);
            return p.executeUpdate() > 0;
        }
    }

    /** Clears the book's summary for the given provider. Returns false if there was none. */
    public boolean delete(UUID bookId, String provider) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "DELETE FROM book_summary WHERE book_id=? AND provider=?")) {
            p.setObject(1, bookId);
            p.setString(2, provider);
            return p.executeUpdate() > 0;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
