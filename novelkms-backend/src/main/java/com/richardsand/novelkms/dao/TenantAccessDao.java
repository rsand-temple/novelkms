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

    public boolean ownsChapter(UUID userId, UUID chapterId) throws SQLException {
        return exists("""
                SELECT 1 FROM chapter c
                JOIN book b ON b.id = c.book_id
                JOIN project p ON p.id = b.project_id
                WHERE c.id = ? AND p.owner_user_id = ?
                """, chapterId, userId);
    }

    public boolean ownsScene(UUID userId, UUID sceneId) throws SQLException {
        return exists("""
                SELECT 1 FROM scene s
                JOIN chapter c ON c.id = s.chapter_id
                JOIN book b ON b.id = c.book_id
                JOIN project p ON p.id = b.project_id
                WHERE s.id = ? AND p.owner_user_id = ?
                """, sceneId, userId);
    }

    public boolean ownsAnyEntity(UUID userId, UUID entityId) throws SQLException {
        return ownsProject(userId, entityId)
                || ownsBook(userId, entityId)
                || ownsPart(userId, entityId)
                || ownsChapter(userId, entityId)
                || ownsScene(userId, entityId);
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
