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
                .aiContextPinned(rs.getBoolean("ai_context_pinned"))
                .structuredData(rs.getString("structured_data"))
                .notes(rs.getString("notes"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Scene> findByChapterId(UUID chapterId) throws SQLException {
        String      sql    = "SELECT * FROM scene WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY display_order, title";
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
        String sql = "SELECT * FROM scene WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Search support
    // -------------------------------------------------------------------------

    /**
     * Lightweight scene row used by SearchService: scene id, its chapter and
     * (nullable) part, plus the raw content HTML. Rows are returned in
     * manuscript reading order so the frontend can walk matches next/previous
     * across the whole book without re-sorting.
     */
    public record SceneContentRow(UUID sceneId, UUID chapterId, UUID partId, String content) {
    }

    /**
     * Returns every scene in the book, in manuscript reading order, with the
     * content HTML needed for full-text search.
     *
     * Ordering mirrors ChapterDao's chapter-numbering CTE — chapters inside
     * parts first (ordered by part display_order then chapter display_order),
     * then direct-book chapters — and is then extended with scene.display_order
     * so scenes within a chapter come back in author order. The trailing
     * s.title is a stable tie-breaker, matching findByChapterId.
     *
     * Note: this selects scene content for the entire book, which may be large
     * when scenes embed base64 images. Acceptable for single-user search;
     * revisit if it becomes a bottleneck (e.g. a stored plain-text shadow
     * column, or chunked/paged search).
     */
    public List<SceneContentRow> findContentForBook(UUID bookId) throws SQLException {
        String sql = "SELECT s.id AS scene_id, s.chapter_id, c.part_id, s.content " +
                "FROM scene s " +
                "JOIN chapter c ON s.chapter_id = c.id " +
                "LEFT JOIN part p ON c.part_id = p.id " +
                "WHERE c.book_id = ? AND s.deleted_at IS NULL AND c.deleted_at IS NULL " +
                "ORDER BY " +
                "  CASE WHEN c.part_id IS NULL THEN 1 ELSE 0 END, " +
                "  p.display_order, " +
                "  c.display_order, " +
                "  s.display_order, " +
                "  s.title";

        List<SceneContentRow> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID sceneId   = rs.getObject("scene_id", UUID.class);
                    UUID chapterId = rs.getObject("chapter_id", UUID.class);
                    // part_id is nullable; mirror ChapterDao's getString + fromString
                    // approach rather than getObject(UUID.class) on a null column.
                    String rawPartId = rs.getString("part_id");
                    UUID   partId    = rawPartId != null ? UUID.fromString(rawPartId) : null;
                    String content   = rs.getString("content");
                    result.add(new SceneContentRow(sceneId, chapterId, partId, content));
                }
            }
        }
        return result;
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
        String sql = """
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
     * Saves scene content and word count.
     * wordCount is supplied by the caller:
     * - From the frontend: TipTap's CharacterCount.words() (single-scene mode)
     * or the countWords() HTML-strip helper (multi-scene mode).
     * - From ImportService.finalizeScene: stripHtml + split on the HTML content.
     * Both approaches use /\S+/ matching so results are consistent with the
     * word count displayed in the editor status bar.
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

    /**
     * Sets the AI-context pin flag on a single scene (a Codex entry). Returns the
     * refreshed scene, or empty if no row matched. Only meaningful for codex
     * entries; the column is inert for manuscript scenes.
     */
    public Optional<Scene> setAiContextPinned(UUID id, boolean pinned) throws SQLException {
        String sql = "UPDATE scene SET ai_context_pinned = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, pinned);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, id);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    /**
     * Bulk-sets the AI-context pin flag on every (non-deleted) entry under one
     * codex category chapter. Returns the number of rows updated. The caller is
     * responsible for confirming the chapter is a codex category.
     */
    public int setAiContextPinnedForChapter(UUID chapterId, boolean pinned) throws SQLException {
        String sql = "UPDATE scene SET ai_context_pinned = ?, updated_at = ? WHERE chapter_id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, pinned);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, chapterId);
            return ps.executeUpdate();
        }
    }

    /**
     * Saves the structured-field JSON for a codex entry. Independent of the
     * rich-text {@code content} save path so the schema-driven form and the
     * long-form body autosave without clobbering each other. Only meaningful for
     * codex entries; the column is inert for manuscript scenes. Returns the
     * refreshed scene, or empty if no row matched.
     */
    public Optional<Scene> saveStructuredData(UUID id, String structuredData) throws SQLException {
        String sql = "UPDATE scene SET structured_data = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, structuredData);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, id);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    public void moveScene(UUID sceneId, UUID newChapterId,
            List<UUID> sourceIds, List<UUID> targetIds) throws SQLException {
        Instant now = Instant.now();
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Re-parent the scene
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE scene SET chapter_id = ?, updated_at = ? WHERE id = ?")) {
                    ps.setObject(1, newChapterId);
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, sceneId);
                    ps.executeUpdate();
                }
                // 2. Renumber source chapter
                if (!sourceIds.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE scene SET display_order = ?, updated_at = ? WHERE id = ?")) {
                        for (int i = 0; i < sourceIds.size(); i++) {
                            ps.setInt(1, i);
                            ps.setTimestamp(2, Timestamp.from(now));
                            ps.setObject(3, sourceIds.get(i));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                // 3. Renumber target chapter (includes moved scene)
                if (!targetIds.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE scene SET display_order = ?, updated_at = ? WHERE id = ?")) {
                        for (int i = 0; i < targetIds.size(); i++) {
                            ps.setInt(1, i);
                            ps.setTimestamp(2, Timestamp.from(now));
                            ps.setObject(3, targetIds.get(i));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("moveScene transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
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

    // -------------------------------------------------------------------------
    // Word count repair
    // -------------------------------------------------------------------------

    /**
     * Recomputes word_count for every scene that has stored content, using the
     * same /\S+/ algorithm that the frontend's countWords() helper and
     * ImportService.finalizeScene() both use.
     *
     * Called via POST /api/admin/recalculate-word-counts to repair scenes whose
     * word_count was zeroed by the pre-fix autosave path (which omitted wordCount
     * from the PUT /scenes/{id}/content request body).
     *
     * Two-phase approach: read all scenes first on one connection, then write
     * all updates in a single batched transaction on a second connection,
     * avoiding holding an open cursor across the update.
     *
     * Returns the number of scenes updated.
     */
    public int recalculateAllWordCounts() throws SQLException {
        // Phase 1 — collect (id, recomputed word count) for every scene with content
        record Update(UUID id, int wordCount) {
        }
        List<Update> updates = new ArrayList<>();

        String selectSql = "SELECT id, content FROM scene WHERE content IS NOT NULL AND content <> ''";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(selectSql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID   id      = rs.getObject("id", UUID.class);
                String content = rs.getString("content");
                updates.add(new Update(id, countWordsFromHtml(content)));
            }
        }

        if (updates.isEmpty()) {
            return 0;
        }

        // Phase 2 — apply all updates in one batched transaction
        String  updateSql = "UPDATE scene SET word_count = ?, updated_at = ? WHERE id = ?";
        Instant now       = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(updateSql)) {
            c.setAutoCommit(false);
            try {
                for (Update u : updates) {
                    ps.setInt(1, u.wordCount());
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, u.id());
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

        return updates.size();
    }

    /**
     * Strips HTML tags and counts non-whitespace tokens (/\S+/ matching),
     * consistent with TipTap's CharacterCount.words() and the frontend's
     * countWords() helper in EditorPanel.jsx.
     */
    private int countWordsFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }
        // Replace every HTML tag with a space so adjacent words across tag
        // boundaries are not merged (e.g. "end</p><p>start" → "end start").
        String text = html.replaceAll("<[^>]+>", " ").trim();
        if (text.isBlank()) {
            return 0;
        }
        // Count non-whitespace runs — same as /\S+/g in JavaScript
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

    // -------------------------------------------------------------------------

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