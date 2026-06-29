package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.auth.OAuthProfile;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.utils.EmailNormalizer;

public class AuthDao {
    public record OAuthState(String stateHash, String provider, String returnPath, Instant expiresAt) {
    }

    public record PendingRegistration(UUID id, String tokenHash, OAuthProfile profile, Instant expiresAt) {
    }

    public record SessionUser(AppUser user, Instant expiresAt) {
    }

    public record UserTrialEligibility(UUID userId, String normalizedEmail, Instant createdAt) {
    }

    private final DataSource dataSource;

    public AuthDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createOAuthState(String stateHash, String provider, String returnPath, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO oauth_state (state_hash, provider, return_path, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stateHash);
            ps.setString(2, provider);
            ps.setString(3, returnPath);
            ps.setTimestamp(4, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public Optional<OAuthState> consumeOAuthState(String stateHash) throws SQLException {
        String select = "SELECT state_hash, provider, return_path, expires_at FROM oauth_state WHERE state_hash = ?";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(select)) {
                ps.setString(1, stateHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return Optional.empty();
                    }
                    OAuthState state = new OAuthState(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant());
                    try (PreparedStatement delete = c.prepareStatement("DELETE FROM oauth_state WHERE state_hash = ?")) {
                        delete.setString(1, stateHash);
                        delete.executeUpdate();
                    }
                    c.commit();
                    return state.expiresAt().isAfter(Instant.now()) ? Optional.of(state) : Optional.empty();
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Optional<AppUser> findUserByIdentity(String provider, String subject) throws SQLException {
        String sql = """
                SELECT u.id, u.email_address, u.normalized_email, u.email_verified, u.first_name, u.last_name,
                       u.display_name, u.mobile_number, u.status, u.created_at, u.updated_at, u.last_login_at
                  FROM app_user u
                  JOIN user_identity i ON i.user_id = u.id
                 WHERE i.provider = ? AND i.provider_subject = ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, provider);
            ps.setString(2, subject);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public boolean normalizedEmailExists(String normalizedEmail) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM app_user WHERE normalized_email = ?")) {
            ps.setString(1, normalizedEmail);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<UserTrialEligibility> trialEligibilityUser(UUID userId) throws SQLException {
        String sql = """
                SELECT id, normalized_email, created_at
                  FROM app_user
                 WHERE id = ?
                   AND status = 'ACTIVE'
                """;

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(new UserTrialEligibility(
                        (UUID) rs.getObject("id"),
                        rs.getString("normalized_email"),
                        rs.getTimestamp("created_at").toInstant()));
            }
        }
    }

    public void touchLogin(UUID userId, String provider, String subject) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement user = c.prepareStatement("UPDATE app_user SET last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?");
                    PreparedStatement identity = c.prepareStatement("UPDATE user_identity SET last_login_at = CURRENT_TIMESTAMP WHERE provider = ? AND provider_subject = ?")) {
                user.setObject(1, userId);
                user.executeUpdate();
                identity.setString(1, provider);
                identity.setString(2, subject);
                identity.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public UUID createPendingRegistration(String tokenHash, OAuthProfile profile, Instant expiresAt) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID id = updatePendingRegistration(c, tokenHash, profile, expiresAt);
                if (id != null) {
                    c.commit();
                    return id;
                }

                id = UUID.randomUUID();
                try {
                    insertPendingRegistration(c, id, tokenHash, profile, expiresAt);
                    c.commit();
                    return id;
                } catch (SQLException e) {
                    if (!isUniqueViolation(e)) {
                        throw e;
                    }

                    // Another request created the pending row between our UPDATE and INSERT.
                    // Refresh that row instead of failing the login flow.
                    id = updatePendingRegistration(c, tokenHash, profile, expiresAt);
                    if (id == null) {
                        throw e;
                    }

                    c.commit();
                    return id;
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Set<String> findRolesForUser(UUID userId) throws SQLException {
        String sql = """
                SELECT role
                  FROM user_role
                 WHERE user_id = ?
                """;

        Set<String> roles = new HashSet<>();

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    if (role != null && !role.isBlank()) {
                        roles.add(role.trim());
                    }
                }
            }
        }

        return Set.copyOf(roles);
    }

    private UUID updatePendingRegistration(Connection c, String tokenHash, OAuthProfile profile, Instant expiresAt) throws SQLException {
        String updateSql = """
                UPDATE pending_registration
                   SET token_hash = ?,
                       email_address = ?,
                       email_verified = ?,
                       suggested_first_name = ?,
                       suggested_last_name = ?,
                       expires_at = ?,
                       consumed_at = NULL
                 WHERE provider = ?
                   AND provider_subject = ?
                """;

        try (PreparedStatement ps = c.prepareStatement(updateSql)) {
            ps.setString(1, tokenHash);
            ps.setString(2, profile.email());
            ps.setBoolean(3, profile.emailVerified());
            ps.setString(4, profile.firstName());
            ps.setString(5, profile.lastName());
            ps.setTimestamp(6, Timestamp.from(expiresAt));
            ps.setString(7, profile.provider());
            ps.setString(8, profile.subject());

            int updated = ps.executeUpdate();
            if (updated == 0) {
                return null;
            }
        }

        String selectSql = """
                SELECT id
                  FROM pending_registration
                 WHERE provider = ?
                   AND provider_subject = ?
                """;

        try (PreparedStatement ps = c.prepareStatement(selectSql)) {
            ps.setString(1, profile.provider());
            ps.setString(2, profile.subject());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Pending registration was updated but could not be re-read");
                }
                return (UUID) rs.getObject("id");
            }
        }
    }

    private void insertPendingRegistration(Connection c, UUID id, String tokenHash, OAuthProfile profile, Instant expiresAt) throws SQLException {
        String insertSql = """
                INSERT INTO pending_registration
                    (id, token_hash, provider, provider_subject, email_address, email_verified,
                     suggested_first_name, suggested_last_name, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = c.prepareStatement(insertSql)) {
            ps.setObject(1, id);
            ps.setString(2, tokenHash);
            ps.setString(3, profile.provider());
            ps.setString(4, profile.subject());
            ps.setString(5, profile.email());
            ps.setBoolean(6, profile.emailVerified());
            ps.setString(7, profile.firstName());
            ps.setString(8, profile.lastName());
            ps.setTimestamp(9, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        for (SQLException x = e; x != null; x = x.getNextException()) {
            if ("23505".equals(x.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    public Optional<PendingRegistration> findPendingRegistration(String tokenHash) throws SQLException {
        String sql = """
                SELECT id, token_hash, provider, provider_subject, email_address, email_verified,
                       suggested_first_name, suggested_last_name, expires_at
                  FROM pending_registration
                 WHERE token_hash = ? AND consumed_at IS NULL
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();
                Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                if (!expiresAt.isAfter(Instant.now()))
                    return Optional.empty();
                OAuthProfile profile = new OAuthProfile(
                        rs.getString("provider"), rs.getString("provider_subject"), rs.getString("email_address"),
                        rs.getBoolean("email_verified"), rs.getString("suggested_first_name"), rs.getString("suggested_last_name"));
                return Optional.of(new PendingRegistration((UUID) rs.getObject("id"), rs.getString("token_hash"), profile, expiresAt));
            }
        }
    }

    public AppUser register(PendingRegistration pending, String firstName, String lastName, String displayName, String mobileNumber) throws SQLException {
        String  normalizedEmail = EmailNormalizer.normalize(pending.profile().email());
        UUID    userId          = UUID.randomUUID();
        UUID    identityId      = UUID.randomUUID();
        Instant now             = Instant.now();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement user = c.prepareStatement("""
                        INSERT INTO app_user
                            (id, email_address, normalized_email, email_verified, first_name, last_name,
                             display_name, mobile_number, status, created_at, updated_at, last_login_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                        """)) {
                    user.setObject(1, userId);
                    user.setString(2, pending.profile().email());
                    user.setString(3, normalizedEmail);
                    user.setBoolean(4, pending.profile().emailVerified());
                    user.setString(5, blankToNull(firstName));
                    user.setString(6, blankToNull(lastName));
                    user.setString(7, displayName.trim());
                    user.setString(8, blankToNull(mobileNumber));
                    user.setTimestamp(9, Timestamp.from(now));
                    user.setTimestamp(10, Timestamp.from(now));
                    user.setTimestamp(11, Timestamp.from(now));
                    user.executeUpdate();
                }
                try (PreparedStatement identity = c.prepareStatement("""
                        INSERT INTO user_identity
                            (id, user_id, provider, provider_subject, provider_email, provider_email_verified, created_at, last_login_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    identity.setObject(1, identityId);
                    identity.setObject(2, userId);
                    identity.setString(3, pending.profile().provider());
                    identity.setString(4, pending.profile().subject());
                    identity.setString(5, pending.profile().email());
                    identity.setBoolean(6, pending.profile().emailVerified());
                    identity.setTimestamp(7, Timestamp.from(now));
                    identity.setTimestamp(8, Timestamp.from(now));
                    identity.executeUpdate();
                }
                try (PreparedStatement consume = c.prepareStatement("UPDATE pending_registration SET consumed_at = CURRENT_TIMESTAMP WHERE id = ? AND consumed_at IS NULL")) {
                    consume.setObject(1, pending.id());
                    if (consume.executeUpdate() != 1)
                        throw new SQLException("Registration was already consumed");
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return new AppUser(userId, pending.profile().email(), normalizedEmail, pending.profile().emailVerified(),
                blankToNull(firstName), blankToNull(lastName), displayName.trim(), blankToNull(mobileNumber),
                "ACTIVE", now, now, now);
    }

    public void createSession(String tokenHash, UUID userId, Instant expiresAt, String ipAddress, String userAgent) throws SQLException {
        String sql = "INSERT INTO user_session (token_hash, user_id, created_at, expires_at, last_seen_at, ip_address, user_agent) VALUES (?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.setObject(2, userId);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.setString(4, ipAddress);
            ps.setString(5, truncate(userAgent, 500));
            ps.executeUpdate();
        }
    }

    public Optional<SessionUser> findSessionUser(String tokenHash) throws SQLException {
        String sql = """
                SELECT u.id, u.email_address, u.normalized_email, u.email_verified, u.first_name, u.last_name,
                       u.display_name, u.mobile_number, u.status, u.created_at, u.updated_at, u.last_login_at,
                       s.expires_at
                  FROM user_session s JOIN app_user u ON u.id = s.user_id
                 WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();
                return Optional.of(new SessionUser(mapUser(rs), rs.getTimestamp("expires_at").toInstant()));
            }
        }
    }

    public void touchSession(String tokenHash) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE user_session SET last_seen_at = CURRENT_TIMESTAMP WHERE token_hash = ?")) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }

    public void revokeSession(String tokenHash) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE user_session SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL")) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }

    private static AppUser mapUser(ResultSet rs) throws SQLException {
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        return new AppUser((UUID) rs.getObject("id"), rs.getString("email_address"), rs.getString("normalized_email"),
                rs.getBoolean("email_verified"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("display_name"), rs.getString("mobile_number"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                lastLogin == null ? null : lastLogin.toInstant());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
