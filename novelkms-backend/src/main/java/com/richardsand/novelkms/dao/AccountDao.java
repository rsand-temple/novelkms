package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.utils.EmailNormalizer;

public class AccountDao {
    public record Account(String email, String first_name, String last_name,
            String display_name, String mobile_number, Instant created_at, Instant last_login_at) {
    }

    private static Account mapUser(ResultSet rs) throws SQLException {
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        return new Account(rs.getString("normalized_email"),
                rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("display_name"), rs.getString("mobile_number"),
                rs.getTimestamp("created_at").toInstant(),
                lastLogin == null ? null : lastLogin.toInstant());
    }

    private final DataSource ds;

    public AccountDao(DataSource dataSource) {
        this.ds = dataSource;
    }

    public Optional<Account> getAccount(UUID id) throws SQLException {
        String sql = """
                SELECT u.id, u.normalized_email, u.first_name, u.last_name, u.display_name,
                       u.mobile_number, u.created_at, u.last_login_at
                FROM app_user AS u 
                WHERE u.id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Account> updateAccount(UUID id, String first_name, String last_name,
            String display_name, String mobile_number, String email) throws SQLException {
        String sql = """
                UPDATE app_user SET app_user.first_name = ?, app_user.last_name = ?, 
                       app_user.display_name = ?, app_user.mobile_number = ?,
                       app_user.updated_at = CURRENT_TIMESTAMP
                WHERE app_user.id = ? AND app_user.normalized_email = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, first_name);
            ps.setString(2, last_name);
            ps.setString(3, display_name);
            ps.setString(4, mobile_number);
            ps.setObject(5, id);
            ps.setObject(6, EmailNormalizer.normalize(email));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }
    
    public boolean delete(UUID id, String normalized_email) throws SQLException {
        String sql = "DELETE FROM app_user WHERE id = ? AND normalized_email = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, normalized_email);
            return ps.executeUpdate() > 0;
        }
    }
}
