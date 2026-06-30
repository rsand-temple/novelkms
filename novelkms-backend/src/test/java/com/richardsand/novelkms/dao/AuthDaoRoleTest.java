package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.auth.Roles;

class AuthDaoRoleTest extends NovelKmsTestBase {
    private AuthDao        dao;

    @BeforeEach
    void setUp() throws Exception {
        dao = new AuthDao(ds);
    }

    @Test
    void findRolesForUserReturnsEmptySetWhenUserHasNoRoles() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "plain-" + userId + "@example.com");

        Set<String> roles = dao.findRolesForUser(userId);

        assertTrue(roles.isEmpty());
    }

    @Test
    void findRolesForUserReturnsAssignedRole() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "admin-" + userId + "@example.com");
        insertRole(userId, Roles.ADMIN);

        Set<String> roles = dao.findRolesForUser(userId);

        assertEquals(Set.of(Roles.ADMIN), roles);
    }

    @Test
    void findRolesForUserReturnsMultipleAssignedRoles() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "support-admin-" + userId + "@example.com");
        insertRole(userId, Roles.ADMIN);
        insertRole(userId, "SUPPORT");

        Set<String> roles = dao.findRolesForUser(userId);

        assertEquals(Set.of(Roles.ADMIN, "SUPPORT"), roles);
    }

    @Test
    void findRolesForUserDoesNotReturnOtherUsersRoles() throws Exception {
        UUID adminUserId  = UUID.randomUUID();
        UUID normalUserId = UUID.randomUUID();

        insertUser(adminUserId, "admin-" + adminUserId + "@example.com");
        insertUser(normalUserId, "normal-" + normalUserId + "@example.com");
        insertRole(adminUserId, Roles.ADMIN);

        Set<String> roles = dao.findRolesForUser(normalUserId);

        assertTrue(roles.isEmpty());
    }

    private void insertUser(UUID userId, String email) throws SQLException {
        String sql = """
                INSERT INTO app_user
                    (id, email_address, normalized_email, email_verified,
                     first_name, last_name, display_name, mobile_number,
                     status, created_at, updated_at, last_login_at)
                VALUES (?, ?, ?, TRUE, NULL, NULL, ?, NULL, 'ACTIVE', ?, ?, ?)
                """;

        Instant now = Instant.now();

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, email.toLowerCase());
            ps.setString(4, email);
            ps.setTimestamp(5, java.sql.Timestamp.from(now));
            ps.setTimestamp(6, java.sql.Timestamp.from(now));
            ps.setTimestamp(7, java.sql.Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertRole(UUID userId, String role) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO user_role (user_id, role) VALUES (?, ?)")) {
            ps.setObject(1, userId);
            ps.setString(2, role);
            ps.executeUpdate();
        }
    }
}