package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;

class AuthDaoTrialEligibilityTest extends NovelKmsTestBase {

    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");

    private AuthDao dao;

    @BeforeEach
    void setUp() throws Exception {
        truncateAll();
        dao = new AuthDao(ds);
    }

    @Test
    void trialEligibilityUserReturnsCreatedAtAndNormalizedEmailForActiveUser() throws Exception {
        Optional<AuthDao.UserTrialEligibility> result = dao.trialEligibilityUser(TEST_USER_ID);

        assertTrue(result.isPresent());
        assertEquals(TEST_USER_ID, result.get().userId());
        assertEquals("test.user@example.com", result.get().normalizedEmail());
    }

    @Test
    void trialEligibilityUserReturnsEmptyForMissingUser() throws Exception {
        Optional<AuthDao.UserTrialEligibility> result = dao.trialEligibilityUser(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    @Test
    void trialEligibilityUserReturnsEmptyForInactiveUser() throws Exception {
        setUserStatus(TEST_USER_ID, "DISABLED");

        Optional<AuthDao.UserTrialEligibility> result = dao.trialEligibilityUser(TEST_USER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void trialEligibilityUserReturnsCreatedAtFromAppUser() throws Exception {
        setUserCreatedAt(TEST_USER_ID, NOW);

        Optional<AuthDao.UserTrialEligibility> result = dao.trialEligibilityUser(TEST_USER_ID);

        assertTrue(result.isPresent());
        assertEquals(NOW, result.get().createdAt());
    }

    private void setUserStatus(UUID userId, String status) throws Exception {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        UPDATE app_user
                           SET status = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """)) {
            ps.setString(1, status);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    private void setUserCreatedAt(UUID userId, Instant createdAt) throws Exception {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        UPDATE app_user
                           SET created_at = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """)) {
            ps.setTimestamp(1, Timestamp.from(createdAt));
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }
}