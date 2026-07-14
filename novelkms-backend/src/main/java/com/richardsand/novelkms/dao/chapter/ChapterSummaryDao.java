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

import com.richardsand.novelkms.dao.book.BookOrder;
import com.richardsand.novelkms.model.chapter.ChapterSummary;

/**
 * Storage for per-chapter summaries and the book-wide ordered read used both to
 * render the aggregated chapter-summary view and to assemble the input for the
 * book summary.
 *
 * <p>Since V36 a chapter holds at most one summary <em>per provider</em>
 * ({@code chapter_summary} is unique on {@code (chapter_id, provider)}); every
 * single-summary operation therefore keys on {@code (chapterId, provider)}, and
 * {@link #upsertGenerated} overwrites that provider's summary in place. The
 * book-wide read {@link #bookChapterSummaries(UUID, String)} resolves ONE summary
 * per chapter using a generating-provider preference (preferred provider's
 * summary, else that chapter's most-recently-updated summary of any provider).
 *
 * <p>Soft-deleted (trashed) chapters are excluded from
 * {@link #bookChapterSummaries} via the {@code deleted_at} filters, so a trashed
 * chapter contributes nothing and shows no status; its rows return when the
 * chapter is restored, and a hard purge cascades them away.
 *
 * <p>This DAO is a deliberate near-clone of {@code ChapterMemoryDao}: chapter
 * summaries and memory documents are independent artifact families sharing the
 * same per-(chapter, provider) shape and the same book-wide numbering CTE, but
 * never the same row.
 */
public class ChapterSummaryDao {

    /**
     * One chapter's row in linear book order, carrying its resolved summary (if
     * any) and its latest scene-content edit time. {@code seq} is the absolute
     * 1-based position in book order (stable across reset-numbering boundaries);
     * {@code chapterNumber} is the displayed number.
     * {@code content}/{@code generatedAt}/{@code source}/{@code provider} are null
     * when the chapter has no summary for the requested preference;
     * {@code contentEditedAt} is null when the chapter has no scenes.
     * {@code provider} identifies which provider variant was resolved (the
     * preferred provider when present, else the fallback document's provider).
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

    public ChapterSummaryDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private ChapterSummary map(ResultSet rs) throws SQLException {
        return ChapterSummary.builder()
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

    // ── Single-summary reads/writes ───────────────────────────────────────────

    /** Returns the chapter's summary for exactly the given provider, if any. */
    public Optional<ChapterSummary> findByChapter(UUID chapterId, String provider) throws SQLException {
        String sql = "SELECT * FROM chapter_summary WHERE chapter_id = ? AND provider = ?";
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
     * Returns the chapter's preferred summary: the {@code preferredProvider}
     * variant when present, otherwise the chapter's most-recently-updated summary
     * of any provider. Returns empty when the chapter has no summary at all. A
     * null/blank {@code preferredProvider} simply yields the most-recent variant.
     */
    public Optional<ChapterSummary> findPreferred(UUID chapterId, String preferredProvider) throws SQLException {
        String sql = "SELECT * FROM chapter_summary WHERE chapter_id = ? "
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

    /** Returns every provider variant of the chapter's summary, newest first. */
    public List<ChapterSummary> findAllByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT * FROM chapter_summary WHERE chapter_id = ? "
                + "ORDER BY updated_at DESC, provider ASC";
        List<ChapterSummary> result = new ArrayList<>();
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
     * Inserts or overwrites the {@code (chapter, provider)} AI-generated summary,
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
                            "UPDATE chapter_summary SET content=?, source='AI', prompt_version=?, model=?,"
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
                            "INSERT INTO chapter_summary(id, chapter_id, provider, content, source, prompt_version, model,"
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
     * Replaces an existing summary's text with author-edited content for the given
     * provider, marking it {@code source = 'EDITED'} and refreshing
     * {@code generatedAt} (an edit is a refresh). Returns false if the chapter has
     * no summary for that provider to edit.
     */
    public boolean updateEdited(UUID chapterId, String provider, String content) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE chapter_summary SET content=?, source='EDITED', generated_at=?, updated_at=?"
                                + " WHERE chapter_id=? AND provider=?")) {
            p.setString(1, content);
            p.setTimestamp(2, Timestamp.from(now));
            p.setTimestamp(3, Timestamp.from(now));
            p.setObject(4, chapterId);
            p.setString(5, provider);
            return p.executeUpdate() > 0;
        }
    }

    /** Clears the chapter's summary for the given provider. Returns false if there was none. */
    public boolean delete(UUID chapterId, String provider) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "DELETE FROM chapter_summary WHERE chapter_id=? AND provider=?")) {
            p.setObject(1, chapterId);
            p.setString(2, provider);
            return p.executeUpdate() > 0;
        }
    }

    // ── Book-wide ordered read ────────────────────────────────────────────────

    /**
     * Returns every (non-trashed) manuscript chapter of the book in linear book
     * order, each with its resolved summary and latest scene-edit time.
     *
     * <p>Reuses the book-wide numbering CTE from {@code ChapterDao} (so chapter
     * numbers match the nav tree, honoring reset-numbering), adds an absolute
     * {@code seq} for unambiguous ordering across reset boundaries, and left-joins
     * ONE summary per chapter chosen by the generating-provider preference: within
     * each chapter, rows are ranked by whether they match {@code preferredProvider}
     * first, then by {@code updated_at} descending, and the top-ranked row is kept.
     * A null/blank {@code preferredProvider} yields the most-recently-updated
     * variant. Also left-joins each chapter's {@code MAX(scene.updated_at)} for
     * content-staleness. Codex chapters ({@code codex_id} not null) are excluded.
     */
    public List<Row> bookChapterSummaries(UUID bookId, String preferredProvider) throws SQLException {
        String sql = "WITH ordered AS ( "
                + "  SELECT c.id, c.resets_numbering, "
                + BookOrder.KEY_COLUMNS
                + "  FROM chapter c "
                + "  LEFT JOIN part p ON c.part_id = p.id "
                + "  WHERE c.book_id = ? AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "), "
                + "grouped AS ( "
                + "  SELECT id, " + BookOrder.KEY_CARRY + ", "
                + "    SUM(CASE WHEN resets_numbering THEN 1 ELSE 0 END) OVER ( "
                + "      ORDER BY " + BookOrder.ORDER_BY + " "
                + "      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW "
                + "    ) AS numbering_group "
                + "  FROM ordered "
                + "), "
                + "numbered AS ( "
                + "  SELECT id, "
                + "    ROW_NUMBER() OVER ( "
                + "      ORDER BY " + BookOrder.ORDER_BY + " "
                + "    ) AS seq, "
                + "    ROW_NUMBER() OVER ( "
                + "      PARTITION BY numbering_group "
                + "      ORDER BY " + BookOrder.ORDER_BY + " "
                + "    ) AS chapter_number "
                + "  FROM grouped "
                + ") "
                + "SELECT c.id, c.title, n.seq, n.chapter_number, "
                + "       cs.content, cs.generated_at, cs.source, cs.provider, "
                + "       sc.content_edited_at "
                + "FROM chapter c "
                + "JOIN numbered n ON c.id = n.id "
                + "LEFT JOIN ( "
                + "  SELECT chapter_id, content, generated_at, source, provider, "
                + "    ROW_NUMBER() OVER ( "
                + "      PARTITION BY chapter_id "
                + "      ORDER BY CASE WHEN provider = ? THEN 0 ELSE 1 END, updated_at DESC "
                + "    ) AS rn "
                + "  FROM chapter_summary "
                + ") cs ON cs.chapter_id = c.id AND cs.rn = 1 "
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
            p.setString(2, preferredProvider); // provider-preference rank in the summary subquery
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
