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

import com.richardsand.novelkms.model.TrashItem;

/**
 * All soft-delete / restore / purge mechanics for the per-user trash can live
 * here, so the existing entity DAOs only need {@code deleted_at IS NULL} added
 * to their live reads.
 *
 * <p>Only the <em>root</em> the user deleted is stamped ({@code deleted_at} +
 * {@code deleted_batch_id}); descendants are hidden transitively because every
 * live read filters {@code deleted_at IS NULL} and a child is only reached
 * through its parent. A {@code trash_batch} row indexes each root for the Trash
 * UI. {@code trash_batch.root_id} has no FK (it points into different tables by
 * {@code root_type}), so dangling rows are cleared by {@link #sweepOrphans}
 * after any purge.
 *
 * <p>The table names passed to {@link #updateSoftDelete} come only from this
 * class's own string constants — never from request input — so there is no SQL
 * injection surface.
 */
public class TrashDao {

    private final BasicDataSource ds;

    public TrashDao(BasicDataSource ds) {
        this.ds = ds;
    }

    /** Current parent pointers of a chapter (any may be null). */
    public record ChapterParents(UUID bookId, UUID partId, UUID codexId) {
    }

    /** The project or book a codex container is scoped to (exactly one is set). */
    public record CodexOwner(UUID projectId, UUID bookId) {
    }

    // =========================================================================
    // Trash batch index
    // =========================================================================

    private TrashItem mapItem(ResultSet rs) throws SQLException {
        return TrashItem.builder()
                .batchId(rs.getObject("id", UUID.class))
                .rootType(rs.getString("root_type"))
                .rootId(rs.getObject("root_id", UUID.class))
                .rootTitle(rs.getString("root_title"))
                .projectId(nullableUuid(rs, "project_id"))
                .projectTitle(rs.getString("project_title"))
                .childCount(rs.getInt("child_count"))
                .deletedAt(rs.getTimestamp("deleted_at").toInstant())
                .build();
    }

    public List<TrashItem> listForUser(UUID userId) throws SQLException {
        String sql = """
                SELECT id, root_type, root_id, root_title, project_id, project_title, child_count, deleted_at
                FROM trash_batch
                WHERE user_id = ?
                ORDER BY deleted_at DESC
                """;
        List<TrashItem> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapItem(rs));
            }
        }
        return result;
    }

    public Optional<TrashItem> findBatch(UUID userId, UUID batchId) throws SQLException {
        String sql = """
                SELECT id, root_type, root_id, root_title, project_id, project_title, child_count, deleted_at
                FROM trash_batch
                WHERE id = ? AND user_id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapItem(rs)) : Optional.empty();
            }
        }
    }

    public void deleteBatch(UUID batchId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM trash_batch WHERE id = ?")) {
            ps.setObject(1, batchId);
            ps.executeUpdate();
        }
    }

    public void deleteAllBatchesForUser(UUID userId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM trash_batch WHERE user_id = ?")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Trash (soft delete) — one transaction each: read context, insert batch,
    // stamp the root. Each is ownership-guarded (owner_user_id / user_id) as
    // defense in depth even though the tenant filter has already authorized.
    // =========================================================================

    public Optional<TrashItem> trashProject(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT p.title,
                       (SELECT COUNT(*) FROM book WHERE project_id = p.id AND deleted_at IS NULL) AS child_count
                FROM project p
                WHERE p.id = ? AND p.owner_user_id = ? AND p.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "project", "PROJECT",
                (rs) -> new Ctx(rs.getString("title"), id, rs.getString("title"), rs.getInt("child_count"), "PROJECT"));
    }

    public Optional<TrashItem> trashBook(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT b.title, b.project_id, p.title AS project_title,
                       (SELECT COUNT(*) FROM chapter WHERE book_id = b.id AND deleted_at IS NULL) AS child_count
                FROM book b
                JOIN project p ON p.id = b.project_id
                WHERE b.id = ? AND p.owner_user_id = ? AND b.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "book", "BOOK",
                (rs) -> new Ctx(rs.getString("title"),
                        rs.getObject("project_id", UUID.class), rs.getString("project_title"),
                        rs.getInt("child_count"), "BOOK"));
    }

    public Optional<TrashItem> trashChapter(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT c.title, c.codex_id,
                       COALESCE(b.project_id, cx.project_id, cb.project_id) AS project_id,
                       p.title AS project_title,
                       (SELECT COUNT(*) FROM scene WHERE chapter_id = c.id AND deleted_at IS NULL) AS child_count
                FROM chapter c
                LEFT JOIN book  b  ON b.id  = c.book_id
                LEFT JOIN codex cx ON cx.id = c.codex_id
                LEFT JOIN book  cb ON cb.id = cx.book_id
                JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id)
                WHERE c.id = ? AND p.owner_user_id = ? AND c.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "chapter", "CHAPTER",
                (rs) -> {
                    boolean codex = rs.getString("codex_id") != null;
                    return new Ctx(rs.getString("title"),
                            rs.getObject("project_id", UUID.class), rs.getString("project_title"),
                            rs.getInt("child_count"), codex ? "CODEX_CATEGORY" : "CHAPTER");
                });
    }

    public Optional<TrashItem> trashScene(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT s.title, c.codex_id,
                       COALESCE(b.project_id, cx.project_id, cb.project_id) AS project_id,
                       p.title AS project_title
                FROM scene s
                JOIN chapter c ON c.id = s.chapter_id
                LEFT JOIN book  b  ON b.id  = c.book_id
                LEFT JOIN codex cx ON cx.id = c.codex_id
                LEFT JOIN book  cb ON cb.id = cx.book_id
                JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id)
                WHERE s.id = ? AND p.owner_user_id = ? AND s.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "scene", "SCENE",
                (rs) -> {
                    boolean codex = rs.getString("codex_id") != null;
                    return new Ctx(rs.getString("title"),
                            rs.getObject("project_id", UUID.class), rs.getString("project_title"),
                            0, codex ? "CODEX_ENTRY" : "SCENE");
                });
    }

    public Optional<TrashItem> trashReview(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT r.project_id, p.title AS project_title, ch.title AS chapter_title,
                       (SELECT COUNT(*) FROM ai_review_recommendation WHERE review_id = r.id) AS child_count
                FROM ai_review r
                LEFT JOIN project p  ON p.id  = r.project_id
                LEFT JOIN chapter ch ON ch.id = r.chapter_id
                WHERE r.id = ? AND r.user_id = ? AND r.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "ai_review", "AI_REVIEW",
                (rs) -> {
                    String chapterTitle = rs.getString("chapter_title");
                    String rootTitle = (chapterTitle == null || chapterTitle.isBlank())
                            ? "AI Review"
                            : "AI Review — " + chapterTitle;
                    return new Ctx(rootTitle,
                            rs.getObject("project_id", UUID.class), rs.getString("project_title"),
                            rs.getInt("child_count"), "AI_REVIEW");
                });
    }

    /**
     * Trashes an artifact folder or file. The root type is ARTIFACT_FOLDER or
     * ARTIFACT_FILE; for a folder, child_count is the number of live direct
     * children (display only). Ownership resolves through the owning project.
     */
    public Optional<TrashItem> trashArtifactNode(UUID userId, UUID id) throws SQLException {
        String ctx = """
                SELECT a.name, a.node_type, a.project_id, p.title AS project_title,
                       (SELECT COUNT(*) FROM artifact_node ch
                          WHERE ch.parent_id = a.id AND ch.deleted_at IS NULL) AS child_count
                FROM artifact_node a
                JOIN project p ON p.id = a.project_id
                WHERE a.id = ? AND p.owner_user_id = ? AND a.deleted_at IS NULL
                """;
        return doTrash(userId, id, ctx, "artifact_node", "ARTIFACT_FILE",
                (rs) -> {
                    boolean folder = "FOLDER".equals(rs.getString("node_type"));
                    return new Ctx(rs.getString("name"),
                            rs.getObject("project_id", UUID.class), rs.getString("project_title"),
                            folder ? rs.getInt("child_count") : 0,
                            folder ? "ARTIFACT_FOLDER" : "ARTIFACT_FILE");
                });
    }

    // ---- shared trash plumbing ----------------------------------------------

    /** Resolved batch context for a soft-delete. */
    private record Ctx(String rootTitle, UUID projectId, String projectTitle, int childCount, String rootType) {
    }

    private interface CtxReader {
        Ctx read(ResultSet rs) throws SQLException;
    }

    private Optional<TrashItem> doTrash(UUID userId, UUID id, String ctxSql, String table,
            String defaultType, CtxReader reader) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Ctx ctx;
                try (PreparedStatement ps = c.prepareStatement(ctxSql)) {
                    ps.setObject(1, id);
                    ps.setObject(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return Optional.empty();
                        }
                        ctx = reader.read(rs);
                    }
                }
                UUID    batchId = UUID.randomUUID();
                Instant now     = Instant.now();
                insertBatch(c, batchId, userId, ctx.rootType(), id, ctx.rootTitle(),
                        ctx.projectId(), ctx.projectTitle(), ctx.childCount(), now);
                updateSoftDelete(c, table, id, batchId, now);
                c.commit();
                return Optional.of(TrashItem.builder()
                        .batchId(batchId).rootType(ctx.rootType()).rootId(id).rootTitle(ctx.rootTitle())
                        .projectId(ctx.projectId()).projectTitle(ctx.projectTitle())
                        .childCount(ctx.childCount()).deletedAt(now)
                        .build());
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void insertBatch(Connection c, UUID batchId, UUID userId, String rootType, UUID rootId,
            String rootTitle, UUID projectId, String projectTitle, int childCount, Instant when) throws SQLException {
        String sql = """
                INSERT INTO trash_batch
                  (id, user_id, root_type, root_id, root_title, project_id, project_title, child_count, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchId);
            ps.setObject(2, userId);
            ps.setString(3, rootType);
            ps.setObject(4, rootId);
            ps.setString(5, rootTitle);
            ps.setObject(6, projectId);
            ps.setString(7, projectTitle);
            ps.setInt(8, childCount);
            ps.setTimestamp(9, Timestamp.from(when));
            ps.executeUpdate();
        }
    }

    private void updateSoftDelete(Connection c, String table, UUID id, UUID batchId, Instant when)
            throws SQLException {
        String sql = "UPDATE " + table
                + " SET deleted_at = ?, deleted_batch_id = ? WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(when));
            ps.setObject(2, batchId);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Raw parent reads (ignore deleted_at) — used by restore to resolve the
    // current parent and to detect orphaned restores.
    // =========================================================================

    public Optional<UUID> bookProjectId(UUID bookId) throws SQLException {
        return scalarUuid("SELECT project_id FROM book WHERE id = ?", bookId);
    }

    public Optional<ChapterParents> chapterParents(UUID chapterId) throws SQLException {
        String sql = "SELECT book_id, part_id, codex_id FROM chapter WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ChapterParents(
                        nullableUuid(rs, "book_id"),
                        nullableUuid(rs, "part_id"),
                        nullableUuid(rs, "codex_id")));
            }
        }
    }

    public Optional<UUID> sceneChapterId(UUID sceneId) throws SQLException {
        return scalarUuid("SELECT chapter_id FROM scene WHERE id = ?", sceneId);
    }

    public Optional<UUID> reviewChapterId(UUID reviewId) throws SQLException {
        return scalarUuid("SELECT chapter_id FROM ai_review WHERE id = ?", reviewId);
    }

    public Optional<CodexOwner> codexOwner(UUID codexId) throws SQLException {
        String sql = "SELECT project_id, book_id FROM codex WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, codexId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new CodexOwner(nullableUuid(rs, "project_id"), nullableUuid(rs, "book_id")));
            }
        }
    }

    /** True when a live (non-trashed) chapter with this id exists. */
    public boolean liveChapterExists(UUID chapterId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM chapter WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // =========================================================================
    // Restore — clear the flag and (for ordered entities) set the de-duplicated
    // title and the append display_order computed by TrashService.
    // =========================================================================

    public boolean restoreProject(UUID id, String title) throws SQLException {
        String sql = "UPDATE project SET deleted_at = NULL, deleted_batch_id = NULL, title = ?, updated_at = ? "
                + "WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean restoreBook(UUID id, String title, int displayOrder) throws SQLException {
        return restoreOrdered("book", id, title, displayOrder);
    }

    public boolean restoreChapter(UUID id, String title, int displayOrder) throws SQLException {
        return restoreOrdered("chapter", id, title, displayOrder);
    }

    public boolean restoreScene(UUID id, String title, int displayOrder) throws SQLException {
        return restoreOrdered("scene", id, title, displayOrder);
    }

    public boolean restoreReview(UUID id) throws SQLException {
        // ai_review has no title / display_order / updated_at.
        String sql = "UPDATE ai_review SET deleted_at = NULL, deleted_batch_id = NULL WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Restores an artifact node, applying the de-duplicated name (and its
     * normalized form, recomputed by {@code TrashService} under the Windows-style
     * case rule) and the append display_order.
     */
    public boolean restoreArtifactNode(UUID id, String name, String nameNormalized, int displayOrder)
            throws SQLException {
        String sql = "UPDATE artifact_node SET deleted_at = NULL, deleted_batch_id = NULL, "
                + "name = ?, name_normalized = ?, display_order = ?, updated_at = ? WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, nameNormalized);
            ps.setInt(3, displayOrder);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setObject(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    private boolean restoreOrdered(String table, UUID id, String title, int displayOrder) throws SQLException {
        String sql = "UPDATE " + table + " SET deleted_at = NULL, deleted_batch_id = NULL, "
                + "title = ?, display_order = ?, updated_at = ? WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, displayOrder);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================================
    // Purge — permanent hard delete. FK ON DELETE CASCADE removes descendant
    // rows (parts, chapters, scenes, codex, recommendations); ai_review has no
    // FK to the manuscript tables, so its rows are removed explicitly first.
    // =========================================================================

    public void purgeProject(UUID id) throws SQLException {
        purgeWithReviews("DELETE FROM ai_review WHERE project_id = ?", "DELETE FROM project WHERE id = ?", id);
    }

    public void purgeBook(UUID id) throws SQLException {
        purgeWithReviews("DELETE FROM ai_review WHERE book_id = ?", "DELETE FROM book WHERE id = ?", id);
    }

    public void purgeChapter(UUID id) throws SQLException {
        purgeWithReviews("DELETE FROM ai_review WHERE chapter_id = ?", "DELETE FROM chapter WHERE id = ?", id);
    }

    public void purgeScene(UUID id) throws SQLException {
        simpleDelete("DELETE FROM scene WHERE id = ?", id);
    }

    public void purgeReview(UUID id) throws SQLException {
        simpleDelete("DELETE FROM ai_review WHERE id = ?", id);
    }

    /**
     * Permanently removes an artifact subtree. Node rows cascade via the
     * self-referential {@code parent_id} FK, but the {@code node -> blob} FK has
     * no cascade, so the subtree's blob storage keys are collected first (for the
     * caller to delete the on-disk bytes after this commits) and the blob rows are
     * deleted explicitly once their referencing nodes are gone. Returns the
     * storage keys whose disk files the caller must remove.
     */
    public List<String> purgeArtifactNode(UUID rootId) throws SQLException {
        String collectSql = """
                WITH RECURSIVE sub(id) AS (
                    SELECT id FROM artifact_node WHERE id = ?
                    UNION ALL
                    SELECT a.id FROM artifact_node a JOIN sub ON a.parent_id = sub.id
                )
                SELECT n.blob_id, b.storage_key
                FROM artifact_node n
                JOIN artifact_blob b ON b.id = n.blob_id
                WHERE n.blob_id IS NOT NULL AND n.id IN (SELECT id FROM sub)
                """;
        List<UUID>   blobIds     = new ArrayList<>();
        List<String> storageKeys = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(collectSql)) {
                    ps.setObject(1, rootId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            blobIds.add(rs.getObject("blob_id", UUID.class));
                            storageKeys.add(rs.getString("storage_key"));
                        }
                    }
                }
                // Delete the subtree (descendants cascade through parent_id).
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM artifact_node WHERE id = ?")) {
                    ps.setObject(1, rootId);
                    ps.executeUpdate();
                }
                // Now the (orphaned) blob rows can be removed.
                if (!blobIds.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM artifact_blob WHERE id = ?")) {
                        for (UUID blobId : blobIds) {
                            ps.setObject(1, blobId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return storageKeys;
    }

    private void purgeWithReviews(String reviewSql, String entitySql, UUID id) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(reviewSql)) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(entitySql)) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void simpleDelete(String sql, UUID id) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Removes trash_batch rows whose root no longer exists. Run after any purge
     * so batches for descendants that were cascade-deleted (e.g. a chapter that
     * was separately trashed under a now-purged book) do not linger.
     */
    public void sweepOrphans(UUID userId) throws SQLException {
        String sql = """
                DELETE FROM trash_batch
                WHERE user_id = ?
                  AND (
                        (root_type = 'PROJECT'
                            AND NOT EXISTS (SELECT 1 FROM project   WHERE id = trash_batch.root_id))
                     OR (root_type = 'BOOK'
                            AND NOT EXISTS (SELECT 1 FROM book      WHERE id = trash_batch.root_id))
                     OR (root_type IN ('CHAPTER', 'CODEX_CATEGORY')
                            AND NOT EXISTS (SELECT 1 FROM chapter   WHERE id = trash_batch.root_id))
                     OR (root_type IN ('SCENE', 'CODEX_ENTRY')
                            AND NOT EXISTS (SELECT 1 FROM scene     WHERE id = trash_batch.root_id))
                     OR (root_type = 'AI_REVIEW'
                            AND NOT EXISTS (SELECT 1 FROM ai_review WHERE id = trash_batch.root_id))
                     OR (root_type IN ('ARTIFACT_FOLDER', 'ARTIFACT_FILE')
                            AND NOT EXISTS (SELECT 1 FROM artifact_node WHERE id = trash_batch.root_id))
                  )
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Optional<UUID> scalarUuid(String sql, UUID key) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(nullableUuid(rs, 1));
            }
        }
    }

    private static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        return raw != null ? UUID.fromString(raw) : null;
    }

    private static UUID nullableUuid(ResultSet rs, int column) throws SQLException {
        String raw = rs.getString(column);
        return raw != null ? UUID.fromString(raw) : null;
    }
}
