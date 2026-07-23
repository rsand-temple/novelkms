package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

public class TenantAccessDao {
    private final BasicDataSource ds;

    public TenantAccessDao(BasicDataSource ds) {
        this.ds = ds;
    }

    public boolean ownsProject(UUID userId, UUID projectId) throws SQLException {
        return exists("SELECT 1 FROM project WHERE id = ? AND owner_user_id = ?", projectId, userId);
    }

    public boolean ownsBook(UUID userId, UUID bookId) throws SQLException {
        return exists("""
                SELECT 1 FROM book b
                JOIN project p ON p.id = b.project_id
                WHERE b.id = ? AND p.owner_user_id = ?
                """, bookId, userId);
    }

    public boolean ownsPart(UUID userId, UUID partId) throws SQLException {
        return exists("""
                SELECT 1 FROM part pt
                JOIN book b ON b.id = pt.book_id
                JOIN project p ON p.id = b.project_id
                WHERE pt.id = ? AND p.owner_user_id = ?
                """, partId, userId);
    }

    /**
     * A codex belongs to a project (series scope) or a book (book scope). Resolve
     * the owning project through whichever is set.
     */
    public boolean ownsCodex(UUID userId, UUID codexId) throws SQLException {
        return exists("""
                SELECT 1 FROM codex cx
                LEFT JOIN book cb ON cb.id = cx.book_id
                JOIN project p ON p.id = COALESCE(cx.project_id, cb.project_id)
                WHERE cx.id = ? AND p.owner_user_id = ?
                """, codexId, userId);
    }

    /**
     * A chapter belongs to a book (manuscript), a codex (world-building), or a
     * book's Scratchpad. The codex itself is either project-scoped or
     * book-scoped, so the owning project is resolved through the first non-null
     * of: the chapter's book, the codex's project, the codex's book, or the
     * Scratchpad's book.
     *
     * <p>The Scratchpad arm is not optional. A Scratchpad chapter deliberately
     * has {@code book_id} NULL — that is what hides it from every book-rooted
     * query — so without it here the chain resolves to no project and the tenant
     * filter 404s the author out of their own Scratchpad. Every query in this
     * codebase that COALESCEs a chapter to its owning project must carry all
     * four arms.
     */
    public boolean ownsChapter(UUID userId, UUID chapterId) throws SQLException {
        return exists("""
                SELECT 1 FROM chapter c
                LEFT JOIN book b   ON b.id  = c.book_id
                LEFT JOIN codex cx ON cx.id = c.codex_id
                LEFT JOIN book cb  ON cb.id = cx.book_id
                LEFT JOIN book sb  ON sb.id = c.scratchpad_book_id
                JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)
                WHERE c.id = ? AND p.owner_user_id = ?
                """, chapterId, userId);
    }

    /**
     * A scene's chapter may be a manuscript chapter, a codex chapter, or a
     * book's Scratchpad; ownership resolves through the same four-arm path as
     * {@link #ownsChapter}.
     */
    public boolean ownsScene(UUID userId, UUID sceneId) throws SQLException {
        return exists("""
                SELECT 1 FROM scene s
                JOIN chapter c ON c.id = s.chapter_id
                LEFT JOIN book b   ON b.id  = c.book_id
                LEFT JOIN codex cx ON cx.id = c.codex_id
                LEFT JOIN book cb  ON cb.id = cx.book_id
                LEFT JOIN book sb  ON sb.id = c.scratchpad_book_id
                JOIN project p ON p.id = COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)
                WHERE s.id = ? AND p.owner_user_id = ?
                """, sceneId, userId);
    }

    /**
     * An artifact node (folder or file) belongs to exactly one project; ownership
     * resolves straight through the project. This also lets the tenant filter's
     * generic JSON-body check accept an artifact {@code parentId} on a move
     * request (see {@link #ownsAnyEntity}).
     */
    public boolean ownsArtifactNode(UUID userId, UUID nodeId) throws SQLException {
        return exists("""
                SELECT 1 FROM artifact_node a
                JOIN project p ON p.id = a.project_id
                WHERE a.id = ? AND p.owner_user_id = ?
                """, nodeId, userId);
    }

    public boolean ownsAnyEntity(UUID userId, UUID entityId) throws SQLException {
        return ownsProject(userId, entityId)
                || ownsBook(userId, entityId)
                || ownsPart(userId, entityId)
                || ownsCodex(userId, entityId)
                || ownsChapter(userId, entityId)
                || ownsScene(userId, entityId)
                || ownsArtifactNode(userId, entityId);
    }

    private boolean exists(String sql, UUID entityId, UUID userId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, entityId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
