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

import javax.sql.DataSource;

import com.richardsand.novelkms.auth.SecretCipher;
import com.richardsand.novelkms.model.AiCredential;

/**
 * Storage for per-user AI provider credentials. The API key is encrypted at rest
 * via {@link SecretCipher}; the plaintext is only ever produced by
 * {@link #getDecryptedKey(UUID, UUID)} at call time. Every query is scoped by
 * {@code user_id} because these endpoints are not covered by the tenant
 * authorization filter (no project/book/etc. UUID appears in their paths).
 */
public class AiCredentialDao {

    private static final String SELECT_COLUMNS =
            "id, user_id, provider, label, default_model, is_default, status, key_last4, created_at, updated_at";

    private final DataSource   dataSource;
    private final SecretCipher cipher;

    public AiCredentialDao(DataSource dataSource, SecretCipher cipher) {
        this.dataSource = dataSource;
        this.cipher = cipher;
    }

    private AiCredential map(ResultSet rs) throws SQLException {
        return AiCredential.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .provider(rs.getString("provider"))
                .label(rs.getString("label"))
                .defaultModel(rs.getString("default_model"))
                .defaultCredential(rs.getBoolean("is_default"))
                .status(rs.getString("status"))
                .keyLast4(rs.getString("key_last4"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .build();
    }

    public List<AiCredential> findByUser(UUID userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ai_credential WHERE user_id = ? "
                + "ORDER BY provider, is_default DESC, label";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AiCredential> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        }
    }

    public Optional<AiCredential> findById(UUID id, UUID userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ai_credential WHERE id = ? AND user_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the user's default credential. v1 is single-provider, so at most
     * one default exists; with multiple providers this returns whichever default
     * was created most recently (the service may also resolve by explicit id).
     */
    public Optional<AiCredential> findDefault(UUID userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ai_credential "
                + "WHERE user_id = ? AND status = 'ACTIVE' "
                + "ORDER BY is_default DESC, created_at DESC";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public String getDecryptedKey(UUID id, UUID userId) throws SQLException {
        String sql = "SELECT api_key_encrypted FROM ai_credential WHERE id = ? AND user_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return cipher.decrypt(rs.getString("api_key_encrypted"));
            }
        }
    }

    /**
     * Creates a credential. If {@code makeDefault} is true, or this is the user's
     * first credential for the provider, it becomes the default for that provider
     * (clearing any sibling default).
     */
    public AiCredential create(UUID userId, String provider, String label, String apiKey,
                               String defaultModel, boolean makeDefault) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String encrypted = cipher.encrypt(apiKey);
        String last4 = lastFour(apiKey);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean isDefault = makeDefault || !providerHasCredential(c, userId, provider);
                if (isDefault) clearProviderDefault(c, userId, provider);

                String sql = "INSERT INTO ai_credential "
                        + "(id, user_id, provider, label, api_key_encrypted, key_last4, default_model, "
                        + " is_default, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, id);
                    ps.setObject(2, userId);
                    ps.setString(3, provider);
                    ps.setString(4, label);
                    ps.setString(5, encrypted);
                    ps.setString(6, last4);
                    ps.setString(7, defaultModel);
                    ps.setBoolean(8, isDefault);
                    ps.setTimestamp(9, Timestamp.from(now));
                    ps.setTimestamp(10, Timestamp.from(now));
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
        return findById(id, userId).orElseThrow();
    }

    /**
     * Updates a credential's label, default model, and (optionally) API key. When
     * {@code apiKey} is null/blank the stored key is left untouched.
     */
    public Optional<AiCredential> update(UUID id, UUID userId, String label, String apiKey,
                                         String defaultModel) throws SQLException {
        Instant now = Instant.now();
        if (apiKey != null && !apiKey.isBlank()) {
            String sql = "UPDATE ai_credential SET label = ?, default_model = ?, "
                    + "api_key_encrypted = ?, key_last4 = ?, updated_at = ? WHERE id = ? AND user_id = ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, label);
                ps.setString(2, defaultModel);
                ps.setString(3, cipher.encrypt(apiKey));
                ps.setString(4, lastFour(apiKey));
                ps.setTimestamp(5, Timestamp.from(now));
                ps.setObject(6, id);
                ps.setObject(7, userId);
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE ai_credential SET label = ?, default_model = ?, updated_at = ? "
                    + "WHERE id = ? AND user_id = ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, label);
                ps.setString(2, defaultModel);
                ps.setTimestamp(3, Timestamp.from(now));
                ps.setObject(4, id);
                ps.setObject(5, userId);
                ps.executeUpdate();
            }
        }
        return findById(id, userId);
    }

    public boolean delete(UUID id, UUID userId) throws SQLException {
        String sql = "DELETE FROM ai_credential WHERE id = ? AND user_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Makes the given credential the default for its provider. */
    public Optional<AiCredential> setDefault(UUID id, UUID userId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String provider;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT provider FROM ai_credential WHERE id = ? AND user_id = ?")) {
                    ps.setObject(1, id);
                    ps.setObject(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return Optional.empty();
                        }
                        provider = rs.getString("provider");
                    }
                }
                clearProviderDefault(c, userId, provider);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ai_credential SET is_default = TRUE, updated_at = ? WHERE id = ? AND user_id = ?")) {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setObject(2, id);
                    ps.setObject(3, userId);
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
        return findById(id, userId);
    }

    private boolean providerHasCredential(Connection c, UUID userId, String provider) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM ai_credential WHERE user_id = ? AND provider = ?")) {
            ps.setObject(1, userId);
            ps.setString(2, provider);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void clearProviderDefault(Connection c, UUID userId, String provider) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ai_credential SET is_default = FALSE WHERE user_id = ? AND provider = ?")) {
            ps.setObject(1, userId);
            ps.setString(2, provider);
            ps.executeUpdate();
        }
    }

    private static String lastFour(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
