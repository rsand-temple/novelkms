package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.model.AdminAuditLogEntry;

class AdminAuditDaoTest {

    private JdbcDataSource dataSource;
    private AdminAuditDao  dao;

    private UUID adminUserId;
    private UUID targetUserId;
    private UUID otherAdminUserId;
    private UUID otherTargetUserId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin_audit_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");

        createSchema();

        dao = new AdminAuditDao(dataSource);

        adminUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        otherAdminUserId = UUID.randomUUID();
        otherTargetUserId = UUID.randomUUID();

        insertUser(adminUserId, "admin@example.com");
        insertUser(targetUserId, "target@example.com");
        insertUser(otherAdminUserId, "other-admin@example.com");
        insertUser(otherTargetUserId, "other-target@example.com");
    }

    @Test
    void recordInsertsAndReturnsAuditEntry() throws Exception {
        AdminAuditLogEntry entry = dao.record(
                adminUserId,
                targetUserId,
                "GRANT_FAMILY_ACCESS",
                "user_subscription",
                targetUserId.toString(),
                "{\"status\":\"none\"}",
                "{\"status\":\"family\"}",
                "Family account");

        assertNotNull(entry.id());
        assertEquals(adminUserId, entry.adminUserId());
        assertEquals(targetUserId, entry.targetUserId());
        assertEquals("GRANT_FAMILY_ACCESS", entry.action());
        assertEquals("user_subscription", entry.entityType());
        assertEquals(targetUserId.toString(), entry.entityId());
        assertEquals("{\"status\":\"none\"}", entry.oldValue());
        assertEquals("{\"status\":\"family\"}", entry.newValue());
        assertEquals("Family account", entry.reason());
        assertNotNull(entry.createdAt());
    }

    @Test
    void findByIdReturnsInsertedEntry() throws Exception {
        AdminAuditLogEntry inserted = dao.record(
                adminUserId,
                targetUserId,
                "EXTEND_TRIAL",
                "user_subscription",
                targetUserId.toString(),
                "trial_end=2026-07-01",
                "trial_end=2026-07-31",
                "Support extension");

        Optional<AdminAuditLogEntry> found = dao.findById(inserted.id());

        assertTrue(found.isPresent());
        assertEquals(inserted.id(), found.get().id());
        assertEquals("EXTEND_TRIAL", found.get().action());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() throws Exception {
        Optional<AdminAuditLogEntry> found = dao.findById(UUID.randomUUID());

        assertTrue(found.isEmpty());
    }

    @Test
    void recentReturnsMostRecentEntriesWithinLimit() throws Exception {
        dao.record(adminUserId, targetUserId, "ACTION_ONE", "user", targetUserId.toString(), null, null, null);
        dao.record(adminUserId, targetUserId, "ACTION_TWO", "user", targetUserId.toString(), null, null, null);
        dao.record(adminUserId, targetUserId, "ACTION_THREE", "user", targetUserId.toString(), null, null, null);

        List<AdminAuditLogEntry> recent = dao.recent(2);

        assertEquals(2, recent.size());
        assertTrue(recent.stream().allMatch(row -> row.action().startsWith("ACTION_")));
    }

    @Test
    void recentUsesDefaultLimitForNullOrInvalidLimit() throws Exception {
        dao.record(adminUserId, targetUserId, "ACTION_ONE", "user", targetUserId.toString(), null, null, null);

        assertEquals(1, dao.recent(null).size());
        assertEquals(1, dao.recent(0).size());
        assertEquals(1, dao.recent(-10).size());
    }

    @Test
    void forTargetUserFiltersByTargetUser() throws Exception {
        dao.record(adminUserId, targetUserId, "TARGET_ACTION", "user", targetUserId.toString(), null, null, null);
        dao.record(adminUserId, otherTargetUserId, "OTHER_TARGET_ACTION", "user", otherTargetUserId.toString(), null, null, null);

        List<AdminAuditLogEntry> rows = dao.forTargetUser(targetUserId, 10);

        assertEquals(1, rows.size());
        assertEquals(targetUserId, rows.get(0).targetUserId());
        assertEquals("TARGET_ACTION", rows.get(0).action());
    }

    @Test
    void byAdminUserFiltersByAdminUser() throws Exception {
        dao.record(adminUserId, targetUserId, "ADMIN_ACTION", "user", targetUserId.toString(), null, null, null);
        dao.record(otherAdminUserId, targetUserId, "OTHER_ADMIN_ACTION", "user", targetUserId.toString(), null, null, null);

        List<AdminAuditLogEntry> rows = dao.byAdminUser(adminUserId, 10);

        assertEquals(1, rows.size());
        assertEquals(adminUserId, rows.get(0).adminUserId());
        assertEquals("ADMIN_ACTION", rows.get(0).action());
    }

    @Test
    void recordAllowsNullTargetAndOptionalFields() throws Exception {
        AdminAuditLogEntry entry = dao.record(
                adminUserId,
                null,
                "SYSTEM_ACTION",
                null,
                null,
                null,
                null,
                null);

        assertNotNull(entry.id());
        assertEquals(adminUserId, entry.adminUserId());
        assertEquals("SYSTEM_ACTION", entry.action());
        assertEquals(null, entry.targetUserId());
        assertEquals(null, entry.entityType());
        assertEquals(null, entry.entityId());
        assertEquals(null, entry.oldValue());
        assertEquals(null, entry.newValue());
        assertEquals(null, entry.reason());
    }

    @Test
    void recordRejectsMissingAdminUserId() {
        assertThrows(IllegalArgumentException.class, () -> dao.record(
                null,
                targetUserId,
                "ACTION",
                "user",
                targetUserId.toString(),
                null,
                null,
                null));
    }

    @Test
    void recordRejectsBlankAction() {
        assertThrows(IllegalArgumentException.class, () -> dao.record(
                adminUserId,
                targetUserId,
                "   ",
                "user",
                targetUserId.toString(),
                null,
                null,
                null));
    }

    @Test
    void forTargetUserRejectsNullTargetUserId() {
        assertThrows(IllegalArgumentException.class, () -> dao.forTargetUser(null, 10));
    }

    @Test
    void byAdminUserRejectsNullAdminUserId() {
        assertThrows(IllegalArgumentException.class, () -> dao.byAdminUser(null, 10));
    }

    @Test
    void recentCapsVeryLargeLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            dao.record(adminUserId, targetUserId, "ACTION_" + i, "user", targetUserId.toString(), null, null, null);
        }

        List<AdminAuditLogEntry> rows = dao.recent(10_000);

        assertFalse(rows.isEmpty());
        assertEquals(3, rows.size());
    }

    private void createSchema() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("""
                    CREATE TABLE app_user (
                        id UUID PRIMARY KEY,
                        email_address VARCHAR(320) NOT NULL,
                        normalized_email VARCHAR(320) NOT NULL UNIQUE,
                        email_verified BOOLEAN NOT NULL,
                        first_name VARCHAR(200),
                        last_name VARCHAR(200),
                        display_name VARCHAR(200) NOT NULL,
                        mobile_number VARCHAR(50),
                        status VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        last_login_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE admin_audit_log (
                        id UUID PRIMARY KEY,
                        admin_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
                        target_user_id UUID NULL REFERENCES app_user(id) ON DELETE SET NULL,
                        action VARCHAR(128) NOT NULL,
                        entity_type VARCHAR(64) NULL,
                        entity_id VARCHAR(128) NULL,
                        old_value CLOB NULL,
                        new_value CLOB NULL,
                        reason CLOB NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT ck_admin_audit_log_action_not_blank CHECK (length(trim(action)) > 0)
                    )
                    """);

            c.createStatement().execute("CREATE INDEX ix_admin_audit_log_admin_user ON admin_audit_log(admin_user_id)");
            c.createStatement().execute("CREATE INDEX ix_admin_audit_log_target_user ON admin_audit_log(target_user_id)");
            c.createStatement().execute("CREATE INDEX ix_admin_audit_log_created_at ON admin_audit_log(created_at)");
            c.createStatement().execute("CREATE INDEX ix_admin_audit_log_action ON admin_audit_log(action)");
        }
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

        try (Connection c = dataSource.getConnection();
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
}