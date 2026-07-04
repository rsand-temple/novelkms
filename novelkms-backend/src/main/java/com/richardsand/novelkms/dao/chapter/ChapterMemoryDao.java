package com.richardsand.novelkms.dao.chapter;

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

import com.richardsand.novelkms.model.chapter.ChapterMemory;

/**
 * Storage for per-chapter memory documents and the book-wide ordered read used
 * both to aggregate preceding chapters' documents into review context and to
 * report memory-document staleness.
 *
 * <p>Since V36 a chapter holds at most one memory document <em>per provider</em>
 * ({@code chapter_memory} is unique on {@code (chapter_id, provider)}); every
 * single-document operation therefore keys on {@code (chapterId, provider)}, and
 * {@link #upsertGenerated} overwrites that provider's document in place.
 *
 * <p>The book-wide read {@link #bookChapterMemory(UUID, String)} resolves ONE
 * document per chapter using a generating-provider preference: the preferred
 * provider's document if the chapter has one, otherwise that chapter's
 * most-recently-updated document of any provider (the agreed fallback). This
 * keeps the "story so far" continuity block and the staleness view single-valued
 * per chapter even though multiple provider variants may exist.
 *
 * <p>Soft-deleted (trashed) chapters are excluded from {@link #bookChapterMemory}
 * via the {@code deleted_at} filters, so a trashed chapter contributes no context
 * and shows no status; its rows return when the chapter is restored, and a hard
 * purge cascades them away.
 */
public class ChapterMemoryDao {

    /**
     * One chapter's row in linear book order, carrying its resolved memory
     * document (if any) and its latest scene-content edit time. {@code seq} is
     * the absolute 1-based position in book order (stable across reset-numbering
     * boundaries); {@code chapterNumber} is the displayed number.
     * {@code content}/{@code generatedAt}/{@code source}/{@code provider} are
     * null when the chapter has no document for the requested preference;
     * {@code contentEditedAt} is null when the chapter has no scenes.
     * {@code provider} identifies which provider variant was resolved for this
     * chapter (equal to the preferred provider when that variant exists, else the
     * provider of the fallback document).
     */
    public record Row(
            UUID chapterId,
            int seq,
            int chapterNumber,
            String title,
            String content,
            Instant generatedAt,
            String source,
            Instant contentEditedAt,
            String provider) {

        public boolean hasDoc() {
            return generatedAt != null;
        }
    }

    private final BasicDataSource ds;

    public ChapterMemoryDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private ChapterMemory map(ResultSet rs) throws SQLException {
        return ChapterMemory.builder()
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

    // ── Single-document reads/writes ──────────────────────────────────────────

    /** Returns the chapter's memory document for exactly the given provider, if any. */
    public Optional<ChapterMemory> findByChapter(UUID chapterId, String provider) throws SQLException {
        String sql = "SELECT * FROM chapter_memory WHERE chapter_id = ? AND provider = ?";
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
     * Returns the chapter's preferred memory document: the {@code preferredProvider}
     * variant when present, otherwise the chapter's most-recently-updated document
     * of any provider. Returns empty when the chapter has no document at all.
     * A null/blank {@code preferredProvider} simply yields the most-recent variant.
     */
    public Optional<ChapterMemory> findPreferred(UUID chapterId, String preferredProvider) throws SQLException {
        String sql = "SELECT * FROM chapter_memory WHERE chapter_id = ? "
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

    /** Returns every provider variant of the chapter's memory document, newest first. */
    public List<ChapterMemory> findAllByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT * FROM chapter_memory WHERE chapter_id = ? "
                + "ORDER BY updated_at DESC, provider ASC";
        List<ChapterMemory> result = new ArrayList<>();
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
     * Inserts or overwrites the {@code (chapter, provider)} AI-generated memory
     * document, refreshing {@code generatedAt}. Sets {@code source = 'AI'}.
     * {@code userGuidance} is the optional one-time author note supplied for this
     * generation (null when none).
     */
    public void upsertGenerated(UUID chapterId, String provider, String content, String promptVersion,
            String model, String userGuidance) throws SQLException {
        Instant now = Instant.now();
        if (findByChapter(chapterId, provider).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE chapter_memory SET content=?, source='AI', prompt_version=?, model=?,"
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
                            "INSERT INTO chapter_memory(id, chapter_id, provider, content, source, prompt_version, model,"
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
     * Replaces an existing document's text with author-edited content for the
     * given provider, marking it {@code source = 'EDITED'} and refreshing
     * {@code generatedAt} (an edit is a refresh). Returns false if the chapter has
     * no document for that provider to edit.
     */
    public boolean updateEdited(UUID chapterId, String provider, String content) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE chapter_memory SET content=?, source='EDITED', generated_at=?, updated_at=?"
                                + " WHERE chapter_id=? AND provider=?")) {
            p.setString(1, content);
            p.setTimestamp(2, Timestamp.from(now));
            p.setTimestamp(3, Timestamp.from(now));
            p.setObject(4, chapterId);
            p.setString(5, provider);
            return p.executeUpdate() > 0;
        }
    }

    /** Clears the chapter's memory document for the given provider. Returns false if there was none. */
    public boolean delete(UUID chapterId, String provider) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "DELETE FROM chapter_memory WHERE chapter_id=? AND provider=?")) {
            p.setObject(1, chapterId);
            p.setString(2, provider);
            return p.executeUpdate() > 0;
        }
    }

    // ── Book-wide ordered read ────────────────────────────────────────────────

    /**
     * Returns every (non-trashed) manuscript chapter of the book in linear book
     * order, each with its resolved memory document and latest scene-edit time.
     *
     * <p>Reuses the book-wide numbering CTE from {@code ChapterDao} (so chapter
     * numbers match the nav tree, honoring reset-numbering), adds an absolute
     * {@code seq} for unambiguous ordering across reset boundaries, and left-joins
     * ONE memory document per chapter chosen by the generating-provider
     * preference: within each chapter, rows are ranked by whether they match
     * {@code preferredProvider} first, then by {@code updated_at} descending, and
     * the top-ranked row is kept. A null/blank {@code preferredProvider} yields
     * the most-recently-updated variant. Also left-joins each chapter's
     * {@code MAX(scene.updated_at)} for content-staleness. Codex chapters
     * ({@code codex_id} not null) are excluded.
     */
    public List<Row> bookChapterMemory(UUID bookId, String preferredProvider) throws SQLException {
        String sql = "WITH ordered AS ( "
                + "  SELECT c.id, c.resets_numbering, "
                + "    CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END AS sort_bucket, "
                + "    p.display_order AS part_order, "
                + "    c.display_order AS chapter_order "
                + "  FROM chapter c "
                + "  LEFT JOIN part p ON c.part_id = p.id "
                + "  WHERE c.book_id = ? AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "), "
                + "grouped AS ( "
                + "  SELECT id, sort_bucket, part_order, chapter_order, "
                + "    SUM(CASE WHEN resets_numbering THEN 1 ELSE 0 END) OVER ( "
                + "      ORDER BY sort_bucket, part_order, chapter_order "
                + "      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW "
                + "    ) AS numbering_group "
                + "  FROM ordered "
                + "), "
                + "numbered AS ( "
                + "  SELECT id, "
                + "    ROW_NUMBER() OVER ( "
                + "      ORDER BY sort_bucket, part_order, chapter_order "
                + "    ) AS seq, "
                + "    ROW_NUMBER() OVER ( "
                + "      PARTITION BY numbering_group "
                + "      ORDER BY sort_bucket, part_order, chapter_order "
                + "    ) AS chapter_number "
                + "  FROM grouped "
                + ") "
                + "SELECT c.id, c.title, n.seq, n.chapter_number, "
                + "       cm.content, cm.generated_at, cm.source, cm.provider, "
                + "       sc.content_edited_at "
                + "FROM chapter c "
                + "JOIN numbered n ON c.id = n.id "
                + "LEFT JOIN ( "
                + "  SELECT chapter_id, content, generated_at, source, provider, "
                + "    ROW_NUMBER() OVER ( "
                + "      PARTITION BY chapter_id "
                + "      ORDER BY CASE WHEN provider = ? THEN 0 ELSE 1 END, updated_at DESC "
                + "    ) AS rn "
                + "  FROM chapter_memory "
                + ") cm ON cm.chapter_id = c.id AND cm.rn = 1 "
                + "LEFT JOIN ( "
                + "  SELECT chapter_id, MAX(updated_at) AS content_edited_at "
                + "  FROM scene WHERE deleted_at IS NULL GROUP BY chapter_id "
                + ") sc ON sc.chapter_id = c.id "
                + "WHERE c.book_id = ? AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "ORDER BY n.seq";

        List<Row> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId);          // ordered CTE
            p.setString(2, preferredProvider); // provider-preference rank in the memory subquery
            p.setObject(3, bookId);          // outer WHERE
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    result.add(new Row(
                            rs.getObject("id", UUID.class),
                            rs.getInt("seq"),
                            rs.getInt("chapter_number"),
                            rs.getString("title"),
                            rs.getString("content"),
                            instant(rs, "generated_at"),
                            rs.getString("source"),
                            instant(rs, "content_edited_at"),
                            rs.getString("provider")));
                }
            }
        }
        return result;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
