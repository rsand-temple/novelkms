package com.richardsand.novelkms.dao;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO for the user-facing NovelKMS archive feature.
 *
 * <p>
 * This class owns JDBC concerns only: selecting export rows, normalizing
 * metadata column names, optional-table detection, and generic row insertion.
 * The service owns the archive format, validation, and source-to-target UUID
 * remapping policy.
 * </p>
 */
public class ArchiveDao {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveDao.class);

    private final BasicDataSource ds;

    public ArchiveDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // ---------------------------------------------------------------------
    // Export selectors
    // ---------------------------------------------------------------------

    public List<Map<String, Object>> findProjectForExport(UUID userId, UUID projectId) throws SQLException {
        return queryRows(
                """
                        SELECT id, title, description, author_first_name, author_last_name,
                               copyright, display_name, email_address, phone_number,
                               created_at, updated_at
                        FROM project
                        WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL
                        """,
                projectId, userId);
    }

    public List<Map<String, Object>> findBooksForProject(UUID projectId) throws SQLException {
        return queryRows(
                """
                        SELECT id, project_id, title, subtitle, short_title, display_order, notes,
                               cover_image, cover_image_mime_type, imported_from, imported_at,
                               created_at, updated_at
                        FROM book
                        WHERE project_id = ? AND deleted_at IS NULL
                        ORDER BY display_order, title
                        """,
                projectId);
    }

    public List<Map<String, Object>> findPartsForProject(UUID projectId) throws SQLException {
        return queryRows(
                """
                        SELECT p.id, p.book_id, p.title, p.subtitle, p.display_order, p.notes,
                               p.created_at, p.updated_at
                        FROM part p
                        JOIN book b ON b.id = p.book_id
                        WHERE b.project_id = ? AND b.deleted_at IS NULL
                        ORDER BY b.display_order, p.display_order
                        """,
                projectId);
    }

    /**
     * Every non-codex chapter of the project's live books, including each book's
     * Scratchpad.
     *
     * <p>The join is on {@code COALESCE(ch.book_id, ch.scratchpad_book_id)}
     * rather than {@code ch.book_id} alone. A Scratchpad chapter has
     * {@code book_id} NULL by design — that is what hides it from the manuscript
     * — so a plain {@code book_id} join would drop it and every scene parked in
     * it straight out of the backup. An archive that silently discards the
     * author's unused scenes is worse than no archive, so the Scratchpad is
     * exported like any other chapter and re-imported by the generic
     * foreign-key remapper, which already rewrites any column ending in
     * {@code _id}.
     */
    public List<Map<String, Object>> findChaptersForProject(UUID projectId) throws SQLException {
        return queryRows(
                """
                        SELECT ch.id, ch.book_id, ch.part_id, ch.codex_id, ch.codex_category,
                               ch.scratchpad_book_id,
                               ch.title, ch.subtitle, ch.display_order, ch.notes,
                               ch.resets_numbering, ch.created_at, ch.updated_at
                        FROM chapter ch
                        JOIN book b ON b.id = COALESCE(ch.book_id, ch.scratchpad_book_id)
                        WHERE b.project_id = ?
                          AND b.deleted_at IS NULL
                          AND ch.codex_id IS NULL
                          AND ch.deleted_at IS NULL
                        ORDER BY b.display_order, ch.display_order
                        """,
                projectId);
    }

    public List<Map<String, Object>> findScenesForChapters(List<UUID> chapterIds) throws SQLException {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return List.of();
        }
        return queryRowsIn(
                """
                        SELECT s.id, s.chapter_id, s.title, s.display_order, s.content,
                               s.word_count, s.notes, s.created_at, s.updated_at
                        FROM scene s
                        WHERE s.chapter_id IN (%s) AND s.deleted_at IS NULL
                        ORDER BY s.display_order, s.title
                        """,
                chapterIds);
    }

    public List<Map<String, Object>> findCodexForProject(UUID projectId, List<UUID> bookIds) {
        return optionalRowsFirstOrIn("codex", """
                SELECT id, project_id, book_id, title, created_at, updated_at
                FROM codex
                WHERE project_id = ? OR book_id IN (%s)
                ORDER BY title
                """, projectId, bookIds);
    }

    public List<Map<String, Object>> findCodexChapters(List<UUID> codexIds) {
        return optionalRows("codex chapters", """
                SELECT id, book_id, part_id, codex_id, codex_category, title, subtitle,
                       display_order, notes, resets_numbering, created_at, updated_at
                FROM chapter
                WHERE codex_id IN (%s) AND deleted_at IS NULL
                ORDER BY display_order, title
                """, codexIds);
    }

    public List<Map<String, Object>> findCodexScenes(List<UUID> codexChapterIds) {
        return optionalRows("codex scenes", """
                SELECT id, chapter_id, title, display_order, content,
                       word_count, notes, created_at, updated_at
                FROM scene
                WHERE chapter_id IN (%s) AND deleted_at IS NULL
                ORDER BY display_order, title
                """, codexChapterIds);
    }

    public List<Map<String, Object>> findAiReviews(List<UUID> chapterIds) {
        return optionalRows("ai_review", """
                SELECT * FROM ai_review
                WHERE chapter_id IN (%s)
                ORDER BY created_at
                """, chapterIds);
    }

    public List<Map<String, Object>> findAiRecommendations(List<UUID> reviewIds) {
        return optionalRows("ai_review_recommendation", """
                SELECT * FROM ai_review_recommendation
                WHERE review_id IN (%s)
                ORDER BY seq, created_at
                """, reviewIds);
    }

    public List<Map<String, Object>> findChapterMemory(List<UUID> chapterIds) {
        return optionalRows("chapter_memory", """
                SELECT * FROM chapter_memory
                WHERE chapter_id IN (%s)
                ORDER BY generated_at
                """, chapterIds);
    }

    public List<Map<String, Object>> findChapterSummaries(List<UUID> chapterIds) {
        return optionalRows("chapter_summary", """
                SELECT * FROM chapter_summary
                WHERE chapter_id IN (%s)
                ORDER BY generated_at
                """, chapterIds);
    }

    public List<Map<String, Object>> findBookSummaries(List<UUID> bookIds) {
        return optionalRows("book_summary", """
                SELECT * FROM book_summary
                WHERE book_id IN (%s)
                ORDER BY generated_at
                """, bookIds);
    }

    public List<Map<String, Object>> findEditorSettings(UUID projectId, List<UUID> bookIds) {
        return optionalRowsFirstOrIn("editor_settings", """
                SELECT * FROM editor_settings
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds);
    }

    public List<Map<String, Object>> findPageLayouts(UUID projectId, List<UUID> bookIds) {
        return optionalRowsFirstOrIn("page_layout", """
                SELECT * FROM page_layout
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds);
    }

    public List<Map<String, Object>> findAiFormInstructions(UUID projectId, List<UUID> bookIds) {
        return optionalRowsFirstOrIn("ai_form_instructions", """
                SELECT * FROM ai_form_instructions
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds);
    }

    public List<Map<String, Object>> findMemoryTemplates(UUID projectId, List<UUID> bookIds) {
        return optionalRowsFirstOrIn("memory_template", """
                SELECT * FROM memory_template
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds);
    }

    // ---------------------------------------------------------------------
    // Import insert helpers
    // ---------------------------------------------------------------------

    public void insertRows(String table, List<Map<String, Object>> rows, List<String> warnings) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        try (Connection c = ds.getConnection()) {
            insertRows(c, table, rows, warnings);
        }
    }

    public void insertRowsInTransaction(List<InsertBatch> batches, List<String> warnings) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (InsertBatch batch : batches) {
                    insertRows(c, batch.table(), batch.rows(), warnings);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public record InsertBatch(String table, List<Map<String, Object>> rows) {
    }

    private void insertRows(Connection c, String table, List<Map<String, Object>> rows, List<String> warnings) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (Map<String, Object> source : rows) {
            LinkedHashMap<String, Object> target = new LinkedHashMap<>(source);
            if (!target.isEmpty()) {
                insertRow(c, table, target);
            }
        }
    }

    private void insertRow(Connection c, String table, LinkedHashMap<String, Object> row) throws SQLException {
        String columns      = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(k -> "?").toList());
        String sql          = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                setParam(ps, i++, e.getKey(), e.getValue());
            }
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------
    // Generic JDBC helpers
    // ---------------------------------------------------------------------

    public List<Map<String, Object>> queryRows(String sql, Object... params) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return queryRows(c, sql, params);
        }
    }

    private List<Map<String, Object>> queryRows(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData         md   = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String column = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
                        Object value  = rs.getObject(i);
                        row.put(column, jsonValue(value));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private List<Map<String, Object>> queryRowsIn(String sqlTemplate, List<UUID> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try (Connection c = ds.getConnection()) {
            return queryRowsIn(c, sqlTemplate, ids);
        }
    }

    private List<Map<String, Object>> queryRowsIn(Connection c, String sqlTemplate, List<UUID> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", ids.stream().map(id -> "?").toList());
        return queryRows(c, sqlTemplate.formatted(placeholders), ids.toArray());
    }

    private List<Map<String, Object>> optionalRows(String label, String sqlTemplate, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try (Connection c = ds.getConnection()) {
            return queryRowsIn(c, sqlTemplate, ids);
        } catch (SQLException e) {
            logger.info("Skipping optional KMS archive section {}: {}", label, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> optionalRowsFirstOrIn(String label, String sqlTemplate, Object first, List<UUID> ids) {
        try (Connection c = ds.getConnection()) {
            String       placeholders = ids == null || ids.isEmpty()
                    ? "NULL"
                    : String.join(", ", ids.stream().map(id -> "?").toList());
            List<Object> params       = new ArrayList<>();
            params.add(first);
            if (ids != null) {
                params.addAll(ids);
            }
            return queryRows(c, sqlTemplate.formatted(placeholders), params.toArray());
        } catch (SQLException e) {
            logger.info("Skipping optional KMS archive section {}: {}", label, e.getMessage());
            return List.of();
        }
    }

    private Object jsonValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u.toString();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Blob blob) {
            long length = blob.length();
            if (length == 0) {
                return "";
            }
            if (length > Integer.MAX_VALUE) {
                throw new SQLException("BLOB too large to export: " + length + " bytes");
            }
            byte[] bytes = blob.getBytes(1, (int) length);
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private void setParam(PreparedStatement ps, int index, String column, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else if ("cover_image".equals(column) && value instanceof String s) {
            ps.setBytes(index, Base64.getDecoder().decode(s));
        } else if (isTimestampColumn(column) && value instanceof String s) {
            ps.setTimestamp(index, Timestamp.from(Instant.parse(s)));
        } else if (isUuidLikeColumn(column) && value instanceof String s) {
            ps.setObject(index, UUID.fromString(s));
        } else {
            ps.setObject(index, value);
        }
    }

    private boolean isUuidLikeColumn(String column) {
        return "id".equals(column) || column.endsWith("_id");
    }

    private boolean isTimestampColumn(String column) {
        return column.endsWith("_at") || column.endsWith("_time") || "timestamp".equals(column);
    }
}
