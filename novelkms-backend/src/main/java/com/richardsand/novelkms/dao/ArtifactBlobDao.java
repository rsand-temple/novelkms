package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * The content-store index for artifact file bytes. One row per uploaded blob
 * (v1 stores one blob per file — no dedup yet; {@code sha256} is captured so a
 * later versioning phase can add content-addressed refcounting).
 *
 * <p>{@code user_id} makes the storage quota a single indexed SUM over a user's
 * blobs across every project they own, including blobs whose file nodes are
 * currently in the trash (trashing frees nothing; only purge does).
 */
public class ArtifactBlobDao {

    private final BasicDataSource ds;

    public ArtifactBlobDao(BasicDataSource ds) {
        this.ds = ds;
    }

    public record BlobRef(UUID id, String storageKey, long sizeBytes, String contentType) {
    }

    /**
     * Inserts a blob row. Runs on the caller's connection so the blob + the file
     * node it backs are committed in one transaction.
     */
    public BlobRef insert(Connection c, UUID userId, String sha256, long sizeBytes,
            String contentType, String storageKey) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
                INSERT INTO artifact_blob (id, user_id, sha256, size_bytes, content_type, storage_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, sha256);
            ps.setLong(4, sizeBytes);
            ps.setString(5, contentType);
            ps.setString(6, storageKey);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.executeUpdate();
        }
        return new BlobRef(id, storageKey, sizeBytes, contentType);
    }

    /** The user's per-user quota override (app_user.artifact_quota_bytes), or empty for the config default. */
    public java.util.Optional<Long> userQuotaOverride(UUID userId) throws SQLException {
        String sql = "SELECT artifact_quota_bytes FROM app_user WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                long v = rs.getLong(1);
                return rs.wasNull() ? java.util.Optional.empty() : java.util.Optional.of(v);
            }
        }
    }

    /** Total bytes a user is currently consuming (counts trashed-but-not-purged files). */
    public long usedBytes(UUID userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(size_bytes), 0) FROM artifact_blob WHERE user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Resolves a file node's blob for download. */
    public java.util.Optional<BlobRef> findById(UUID id) throws SQLException {
        String sql = "SELECT id, storage_key, size_bytes, content_type FROM artifact_blob WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new BlobRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("storage_key"),
                        rs.getLong("size_bytes"),
                        rs.getString("content_type")));
            }
        }
    }

    /** Deletes a blob row. Runs on the caller's connection for transactional safety. */
    public void delete(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM artifact_blob WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }
}
