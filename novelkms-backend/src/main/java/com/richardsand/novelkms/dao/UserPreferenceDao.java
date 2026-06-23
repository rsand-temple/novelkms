package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * Flat per-user UI preference store ({@code user_preference}).
 *
 * <p>A simple key/value bag scoped to {@code app_user}. Each query is
 * {@code user_id}-scoped (the {@code /preferences} path carries no tenant UUID,
 * so the tenant filter does not cover it; ownership is enforced here and in the
 * resource via {@code CurrentUser}). Values are opaque strings (scalars or JSON)
 * — the meaning of each key lives in the frontend.
 */
public class UserPreferenceDao {

    private final BasicDataSource ds;

    public UserPreferenceDao(BasicDataSource ds) {
        this.ds = ds;
    }

    /** All preferences for a user as an ordered key -> value map. */
    public Map<String, String> getAll(UUID userId) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "SELECT pref_key, pref_value FROM user_preference WHERE user_id=? ORDER BY pref_key")) {
            p.setObject(1, userId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.put(r.getString("pref_key"), r.getString("pref_value"));
            }
        }
        return out;
    }

    public Optional<String> get(UUID userId, String key) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "SELECT pref_value FROM user_preference WHERE user_id=? AND pref_key=?")) {
            p.setObject(1, userId);
            p.setString(2, key);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.ofNullable(r.getString("pref_value")) : Optional.empty();
            }
        }
    }

    /**
     * Insert-or-update a single preference. UPDATE first; if no existing row,
     * INSERT. Portable across H2 and PostgreSQL (no dialect-specific upsert),
     * and safe under the single-user-per-key uniqueness constraint.
     */
    public void upsert(UUID userId, String key, String value) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE user_preference SET pref_value=?, updated_at=? WHERE user_id=? AND pref_key=?")) {
                up.setString(1, value);
                up.setTimestamp(2, Timestamp.from(now));
                up.setObject(3, userId);
                up.setString(4, key);
                if (up.executeUpdate() > 0) return;
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO user_preference(id,user_id,pref_key,pref_value,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,?)")) {
                ins.setObject(1, UUID.randomUUID());
                ins.setObject(2, userId);
                ins.setString(3, key);
                ins.setString(4, value);
                ins.setTimestamp(5, Timestamp.from(now));
                ins.setTimestamp(6, Timestamp.from(now));
                ins.executeUpdate();
            }
        }
    }

    public boolean delete(UUID userId, String key) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "DELETE FROM user_preference WHERE user_id=? AND pref_key=?")) {
            p.setObject(1, userId);
            p.setString(2, key);
            return p.executeUpdate() > 0;
        }
    }
}
