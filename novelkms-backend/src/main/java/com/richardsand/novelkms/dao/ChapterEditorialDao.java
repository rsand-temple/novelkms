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

import com.richardsand.novelkms.model.ChapterEditorial;

/**
 * Storage for per-chapter editorials.
 *
 * <p>There is at most one current editorial per chapter ({@code chapter_id} is
 * unique); {@link #upsertGenerated} overwrites in place. A soft-deleted (trashed)
 * chapter keeps its row and it reappears on restore; a hard purge cascades it
 * away via {@code ON DELETE CASCADE}.
 *
 * <p>Unlike {@code ChapterMemoryDao} and {@code ChapterSummaryDao}, this DAO has
 * no book-wide ordered read: an editorial is never aggregated, never consumed by
 * another AI function, and has no coverage/staleness view — it is a purely
 * author-facing single document. So this DAO is deliberately just the
 * one-per-chapter CRUD.
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

    public Optional<ChapterEditorial> findByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT * FROM chapter_editorial WHERE chapter_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, chapterId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserts or overwrites the chapter's AI-generated editorial, refreshing
     * {@code generatedAt}. Sets {@code source = 'AI'}. {@code userGuidance} is the
     * optional one-time author note supplied for this generation (null when none).
     */
    public void upsertGenerated(UUID chapterId, String content, String promptVersion, String model,
            String userGuidance) throws SQLException {
        Instant now = Instant.now();
        if (findByChapter(chapterId).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE chapter_editorial SET content=?, source='AI', prompt_version=?, model=?,"
                                    + " user_guidance=?, generated_at=?, updated_at=? WHERE chapter_id=?")) {
                p.setString(1, content);
                p.setString(2, promptVersion);
                p.setString(3, model);
                p.setString(4, userGuidance);
                p.setTimestamp(5, Timestamp.from(now));
                p.setTimestamp(6, Timestamp.from(now));
                p.setObject(7, chapterId);
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO chapter_editorial(id, chapter_id, content, source, prompt_version, model,"
                                    + " user_guidance, generated_at, created_at, updated_at)"
                                    + " VALUES (?,?,?,'AI',?,?,?,?,?,?)")) {
                p.setObject(1, UUID.randomUUID());
                p.setObject(2, chapterId);
                p.setString(3, content);
                p.setString(4, promptVersion);
                p.setString(5, model);
                p.setString(6, userGuidance);
                p.setTimestamp(7, Timestamp.from(now));
                p.setTimestamp(8, Timestamp.from(now));
                p.setTimestamp(9, Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    /**
     * Replaces an existing editorial's text with author-edited content, marking it
     * {@code source = 'EDITED'} and refreshing {@code generatedAt} (an edit is a
     * refresh). Returns false if the chapter has no editorial to edit.
     */
    public boolean updateEdited(UUID chapterId, String content) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE chapter_editorial SET content=?, source='EDITED', generated_at=?, updated_at=?"
                                + " WHERE chapter_id=?")) {
            p.setString(1, content);
            p.setTimestamp(2, Timestamp.from(now));
            p.setTimestamp(3, Timestamp.from(now));
            p.setObject(4, chapterId);
            return p.executeUpdate() > 0;
        }
    }

    public boolean delete(UUID chapterId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM chapter_editorial WHERE chapter_id=?")) {
            p.setObject(1, chapterId);
            return p.executeUpdate() > 0;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
