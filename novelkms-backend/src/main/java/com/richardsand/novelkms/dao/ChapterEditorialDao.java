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

import com.richardsand.novelkms.model.ChapterEditorial;

/**
 * Storage for per-chapter editorials.
 *
 * <p>Since V36 a chapter holds at most one editorial <em>per provider</em>
 * ({@code chapter_editorial} is unique on {@code (chapter_id, provider)}); every
 * operation keys on {@code (chapterId, provider)} and {@link #upsertGenerated}
 * overwrites that provider's editorial in place. A soft-deleted (trashed) chapter
 * keeps its rows and they reappear on restore; a hard purge cascades them away
 * via {@code ON DELETE CASCADE}.
 *
 * <p>Unlike {@code ChapterMemoryDao} and {@code ChapterSummaryDao}, this DAO has
 * no book-wide ordered read: an editorial is never aggregated, never consumed by
 * another AI function, and has no coverage/staleness view — it is a purely
 * author-facing document. So this DAO is deliberately just the per-(chapter,
 * provider) CRUD plus the variant reads the UI selector needs.
 */
public class ChapterEditorialDao {

    private final BasicDataSource ds;

    public ChapterEditorialDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private ChapterEditorial map(ResultSet rs) throws SQLException {
        return ChapterEditorial.builder()
                .id(rs.getObject("id", UUID.class))
                .chapterId(rs.getObject("chapter_id", UUID.class))
                .provider(rs.getString("provider"))
                .content(rs.getString("content"))
                .source(rs.getString("source"))
                .promptVersion(rs.getString("prompt_version"))
                .model(rs.getString("model"))
                .userGuidance(rs.getString("user_guidance"))
                .generatedAt(instant(rs, "generated_at"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }

    /** Returns the chapter's editorial for exactly the given provider, if any. */
    public Optional<ChapterEditorial> findByChapter(UUID chapterId, String provider) throws SQLException {
        String sql = "SELECT * FROM chapter_editorial WHERE chapter_id = ? AND provider = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, chapterId);
            p.setString(2, provider);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the chapter's preferred editorial: the {@code preferredProvider}
     * variant when present, otherwise the chapter's most-recently-updated editorial
     * of any provider. Returns empty when the chapter has no editorial at all. A
     * null/blank {@code preferredProvider} simply yields the most-recent variant.
     */
    public Optional<ChapterEditorial> findPreferred(UUID chapterId, String preferredProvider) throws SQLException {
        String sql = "SELECT * FROM chapter_editorial WHERE chapter_id = ? "
                + "ORDER BY CASE WHEN provider = ? THEN 0 ELSE 1 END, updated_at DESC";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, chapterId);
            p.setString(2, preferredProvider);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Returns every provider variant of the chapter's editorial, newest first. */
    public List<ChapterEditorial> findAllByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT * FROM chapter_editorial WHERE chapter_id = ? "
                + "ORDER BY updated_at DESC, provider ASC";
        List<ChapterEditorial> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, chapterId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /**
     * Inserts or overwrites the {@code (chapter, provider)} AI-generated editorial,
     * refreshing {@code generatedAt}. Sets {@code source = 'AI'}.
     * {@code userGuidance} is the optional one-time author note supplied for this
     * generation (null when none).
     */
    public void upsertGenerated(UUID chapterId, String provider, String content, String promptVersion,
            String model, String userGuidance) throws SQLException {
        Instant now = Instant.now();
        if (findByChapter(chapterId, provider).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE chapter_editorial SET content=?, source='AI', prompt_version=?, model=?,"
                                    + " user_guidance=?, generated_at=?, updated_at=? WHERE chapter_id=? AND provider=?")) {
                p.setString(1, content);
                p.setString(2, promptVersion);
                p.setString(3, model);
                p.setString(4, userGuidance);
                p.setTimestamp(5, Timestamp.from(now));
                p.setTimestamp(6, Timestamp.from(now));
                p.setObject(7, chapterId);
                p.setString(8, provider);
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO chapter_editorial(id, chapter_id, provider, content, source, prompt_version, model,"
                                    + " user_guidance, generated_at, created_at, updated_at)"
                                    + " VALUES (?,?,?,?,'AI',?,?,?,?,?,?)")) {
                p.setObject(1, UUID.randomUUID());
                p.setObject(2, chapterId);
                p.setString(3, provider);
                p.setString(4, content);
                p.setString(5, promptVersion);
                p.setString(6, model);
                p.setString(7, userGuidance);
                p.setTimestamp(8, Timestamp.from(now));
                p.setTimestamp(9, Timestamp.from(now));
                p.setTimestamp(10, Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    /**
     * Replaces an existing editorial's text with author-edited content for the
     * given provider, marking it {@code source = 'EDITED'} and refreshing
     * {@code generatedAt} (an edit is a refresh). Returns false if the chapter has
     * no editorial for that provider to edit.
     */
    public boolean updateEdited(UUID chapterId, String provider, String content) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE chapter_editorial SET content=?, source='EDITED', generated_at=?, updated_at=?"
                                + " WHERE chapter_id=? AND provider=?")) {
            p.setString(1, content);
            p.setTimestamp(2, Timestamp.from(now));
            p.setTimestamp(3, Timestamp.from(now));
            p.setObject(4, chapterId);
            p.setString(5, provider);
            return p.executeUpdate() > 0;
        }
    }

    /** Clears the chapter's editorial for the given provider. Returns false if there was none. */
    public boolean delete(UUID chapterId, String provider) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "DELETE FROM chapter_editorial WHERE chapter_id=? AND provider=?")) {
            p.setObject(1, chapterId);
            p.setString(2, provider);
            return p.executeUpdate() > 0;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
