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

import com.richardsand.novelkms.model.ChapterSummary;

/**
 * Storage for per-chapter summaries and the book-wide ordered read used both to
 * render the aggregated chapter-summary view and to assemble the input for the
 * book summary.
 *
 * <p>There is at most one current summary per chapter ({@code chapter_id} is
 * unique); {@link #upsertGenerated} overwrites in place. Soft-deleted (trashed)
 * chapters are excluded from {@link #bookChapterSummaries} via the
 * {@code deleted_at} filters, so a trashed chapter contributes nothing and shows
 * no status; the row returns when the chapter is restored, and a hard purge
 * cascades it away.
 *
 * <p>This DAO is a deliberate near-clone of {@code ChapterMemoryDao}: chapter
 * summaries and memory documents are independent artifact families sharing the
 * same one-per-chapter shape and the same book-wide numbering CTE, but never the
 * same row.
 */
public class ChapterSummaryDao {

    /**
     * One chapter's row in linear book order, carrying its current summary (if
     * any) and its latest scene-content edit time. {@code seq} is the absolute
     * 1-based position in book order (stable across reset-numbering boundaries);
     * {@code chapterNumber} is the displayed number.
     * {@code content}/{@code generatedAt}/{@code source} are null when the chapter
     * has no summary; {@code contentEditedAt} is null when the chapter has no
     * scenes.
     */
    public record Row(
            UUID chapterId,
            int seq,
            int chapterNumber,
            String title,
            String content,
            Instant generatedAt,
            String source,
            Instant contentEditedAt) {

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
                .content(rs.getString("content"))
                .source(rs.getString("source"))
                .promptVersion(rs.getString("prompt_version"))
                .model(rs.getString("model"))
                .generatedAt(instant(rs, "generated_at"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }

    // ── Single-summary reads/writes ───────────────────────────────────────────

    public Optional<ChapterSummary> findByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT * FROM chapter_summary WHERE chapter_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, chapterId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserts or overwrites the chapter's AI-generated summary, refreshing
     * {@code generatedAt}. Sets {@code source = 'AI'}.
     */
    public void upsertGenerated(UUID chapterId, String content, String promptVersion, String model)
            throws SQLException {
        Instant now = Instant.now();
        if (findByChapter(chapterId).isPresent()) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE chapter_summary SET content=?, source='AI', prompt_version=?, model=?,"
                                    + " generated_at=?, updated_at=? WHERE chapter_id=?")) {
                p.setString(1, content);
                p.setString(2, promptVersion);
                p.setString(3, model);
                p.setTimestamp(4, Timestamp.from(now));
                p.setTimestamp(5, Timestamp.from(now));
                p.setObject(6, chapterId);
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO chapter_summary(id, chapter_id, content, source, prompt_version, model,"
                                    + " generated_at, created_at, updated_at)"
                                    + " VALUES (?,?,?,'AI',?,?,?,?,?)")) {
                p.setObject(1, UUID.randomUUID());
                p.setObject(2, chapterId);
                p.setString(3, content);
                p.setString(4, promptVersion);
                p.setString(5, model);
                p.setTimestamp(6, Timestamp.from(now));
                p.setTimestamp(7, Timestamp.from(now));
                p.setTimestamp(8, Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    /**
     * Replaces an existing summary's text with author-edited content, marking it
     * {@code source = 'EDITED'} and refreshing {@code generatedAt} (an edit is a
     * refresh). Returns false if the chapter has no summary to edit.
     */
    public boolean updateEdited(UUID chapterId, String content) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE chapter_summary SET content=?, source='EDITED', generated_at=?, updated_at=?"
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
                PreparedStatement p = c.prepareStatement("DELETE FROM chapter_summary WHERE chapter_id=?")) {
            p.setObject(1, chapterId);
            return p.executeUpdate() > 0;
        }
    }

    // ── Book-wide ordered read ────────────────────────────────────────────────

    /**
     * Returns every (non-trashed) manuscript chapter of the book in linear book
     * order, each with its current summary and latest scene-edit time.
     *
     * <p>Reuses the book-wide numbering CTE from {@code ChapterDao} (so chapter
     * numbers match the nav tree, honoring reset-numbering), adds an absolute
     * {@code seq} for unambiguous ordering across reset boundaries, left-joins the
     * one-per-chapter summary, and left-joins each chapter's
     * {@code MAX(scene.updated_at)} for content-staleness. Codex chapters
     * ({@code codex_id} not null) are excluded.
     */
    public List<Row> bookChapterSummaries(UUID bookId) throws SQLException {
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
                + "       cs.content, cs.generated_at, cs.source, "
                + "       sc.content_edited_at "
                + "FROM chapter c "
                + "JOIN numbered n ON c.id = n.id "
                + "LEFT JOIN chapter_summary cs ON cs.chapter_id = c.id "
                + "LEFT JOIN ( "
                + "  SELECT chapter_id, MAX(updated_at) AS content_edited_at "
                + "  FROM scene WHERE deleted_at IS NULL GROUP BY chapter_id "
                + ") sc ON sc.chapter_id = c.id "
                + "WHERE c.book_id = ? AND c.codex_id IS NULL AND c.deleted_at IS NULL "
                + "ORDER BY n.seq";

        List<Row> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, bookId); // ordered CTE
            p.setObject(2, bookId); // outer WHERE
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
                            instant(rs, "content_edited_at")));
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
