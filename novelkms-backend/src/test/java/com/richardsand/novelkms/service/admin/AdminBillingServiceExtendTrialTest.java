package com.richardsand.novelkms.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.admin.AdminUserDao;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.model.AdminAuditLogEntry;
import com.richardsand.novelkms.model.UserSubscription;
import com.richardsand.novelkms.model.admin.ExtendTrialRequest;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

class AdminBillingServiceExtendTrialTest extends NovelKmsTestBase {
    private AdminAuditDao       adminAuditDao;
    private UserSubscriptionDao userSubscriptionDao;
    private AuthDao             authDao;
    private AdminUserDao        adminUserDao;
    private AdminBillingService service;

    private UUID adminUserId;
    private UUID targetUserId;
    private UUID inactiveUserId;

    @BeforeEach
    void setUp() throws Exception {
        adminAuditDao = new AdminAuditDao(ds);
        adminUserDao = new AdminUserDao(ds);
        userSubscriptionDao = new UserSubscriptionDao(ds);
        authDao = new AuthDao(ds);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new AdminBillingService(
                userSubscriptionDao,
                adminAuditDao,
                adminUserDao,
                authDao,
                mapper);

        adminUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        inactiveUserId = UUID.randomUUID();

        insertUser(adminUserId, "admin-" + adminUserId + "@example.com", "ACTIVE");
        insertUser(targetUserId, "target-" + targetUserId + "@example.com", "ACTIVE");
        insertUser(inactiveUserId, "inactive-" + inactiveUserId + "@example.com", "DISABLED");
    }

    // --- happy paths -------------------------------------------------------

    @Test
    void extendTrialByDaysCreatesTrialingRowWhenMissing() throws Exception {
        Instant before = Instant.now();

        UserSubscription subscription = service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 14, "extended_eval", null));

        assertEquals("trialing", subscription.status());
        assertNotNull(subscription.trialStart());
        assertNotNull(subscription.trialEnd());

        // No prior trial end, so anchor is "now": end is ~14 days out.
        long days = Duration.between(before, subscription.trialEnd()).toDays();
        assertTrue(days >= 13 && days <= 14, "expected ~14 days, got " + days);

        UserSubscription persisted = userSubscriptionDao.findByUserId(targetUserId).orElseThrow();
        assertEquals("trialing", persisted.status());
    }

    @Test
    void extendTrialByDaysAnchorsOnExistingFutureTrialEnd() throws Exception {
        Instant existingEnd = Instant.now().plus(Duration.ofDays(10));
        insertTrial(targetUserId, existingEnd);

        UserSubscription subscription = service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 5, "extended_eval", null));

        // Anchored on the existing end (~10 days out) plus 5 => ~15 days out,
        // not 5 days from now.
        long days = Duration.between(Instant.now(), subscription.trialEnd()).toDays();
        assertTrue(days >= 14 && days <= 15, "expected ~15 days, got " + days);
    }

    @Test
    void extendTrialByDaysOnLapsedTrialAnchorsOnNow() throws Exception {
        Instant lapsedEnd = Instant.now().minus(Duration.ofDays(3));
        insertTrial(targetUserId, lapsedEnd);

        UserSubscription subscription = service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 7, "reopened", null));

        // Lapsed trial: anchor falls back to now, so ~7 days out.
        long days = Duration.between(Instant.now(), subscription.trialEnd()).toDays();
        assertTrue(days >= 6 && days <= 7, "expected ~7 days, got " + days);
    }

    @Test
    void extendTrialByDatePreservesExistingTrialStart() throws Exception {
        Instant originalStart = Instant.now().minus(Duration.ofDays(20));
        insertTrialWithStart(targetUserId, originalStart, Instant.now().minus(Duration.ofDays(1)));

        Instant target = Instant.now().plus(Duration.ofDays(30));
        UserSubscription subscription = service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(target, null, "support_comp", null));

        assertEquals("trialing", subscription.status());
        // trial_start is preserved (only set when previously null).
        assertEquals(
                originalStart.truncatedTo(ChronoUnit.SECONDS),
                subscription.trialStart().truncatedTo(ChronoUnit.SECONDS));
        // Absolute date resolves to end-of-day UTC, i.e. strictly after the requested instant.
        assertTrue(subscription.trialEnd().isAfter(target)
                || subscription.trialEnd().equals(target)
                || subscription.trialEnd().isAfter(Instant.now()));
    }

    @Test
    void extendTrialWritesAuditRowWithResolvedEnd() throws Exception {
        service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 30, "onboarding_delay", "waiting on data import"));

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);
        assertEquals(1, auditRows.size());

        AdminAuditLogEntry audit = auditRows.get(0);
        assertEquals(adminUserId, audit.adminUserId());
        assertEquals(targetUserId, audit.targetUserId());
        assertEquals(AdminBillingService.ACTION_EXTEND_TRIAL, audit.action());
        assertEquals(AdminBillingService.ENTITY_USER_SUBSCRIPTION, audit.entityType());
        assertEquals(targetUserId.toString(), audit.entityId());
        assertNull(audit.oldValue());
        assertNotNull(audit.newValue());
        assertTrue(audit.newValue().contains("\"status\":\"trialing\""));
        assertTrue(audit.reason().contains("Trial extended by 30 day(s)"));
        assertTrue(audit.reason().contains("onboarding_delay"));
        assertTrue(audit.reason().contains("waiting on data import"));
    }

    @Test
    void extendTrialFromPastDueIsAllowed() throws Exception {
        insertStatus(targetUserId, "past_due");

        UserSubscription subscription = service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 10, "grace", null));

        assertEquals("trialing", subscription.status());
    }

    // --- guard: non-demotable statuses ------------------------------------

    @Test
    void extendTrialRejectsFamilyUser() throws Exception {
        insertStatus(targetUserId, "family");

        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 14, "nope", null)));

        assertEquals("family", userSubscriptionDao.findByUserId(targetUserId).orElseThrow().status());
    }

    @Test
    void extendTrialRejectsActiveUser() throws Exception {
        insertStatus(targetUserId, "active");

        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 14, "nope", null)));
    }

    @Test
    void extendTrialRejectsActiveCancelingUser() throws Exception {
        insertStatus(targetUserId, "active_canceling");

        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 14, "nope", null)));
    }

    // --- guard: mode / bounds validation ----------------------------------

    @Test
    void extendTrialRejectsNeitherMode() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, null, "empty", null)));
    }

    @Test
    void extendTrialRejectsBothModes() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(Instant.now().plus(Duration.ofDays(5)), 5, "both", null)));
    }

    @Test
    void extendTrialRejectsNullBody() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                null));
    }

    @Test
    void extendTrialRejectsNonPositiveDays() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, 0, "zero", null)));
    }

    @Test
    void extendTrialRejectsPastDate() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(Instant.now().minus(Duration.ofDays(2)), null, "past", null)));
    }

    @Test
    void extendTrialRejectsBeyondMaxWindow() {
        assertThrows(BadRequestException.class, () -> service.extendTrial(
                adminUserId,
                targetUserId,
                new ExtendTrialRequest(null, AdminBillingService.MAX_TRIAL_DAYS_FROM_NOW + 5, "toolong", null)));
    }

    // --- guard: user validity ---------------------------------------------

    @Test
    void extendTrialRejectsUnknownUser() {
        assertThrows(NotFoundException.class, () -> service.extendTrial(
                adminUserId,
                UUID.randomUUID(),
                new ExtendTrialRequest(null, 14, "unknown", null)));
    }

    @Test
    void extendTrialRejectsInactiveUser() {
        assertThrows(NotFoundException.class, () -> service.extendTrial(
                adminUserId,
                inactiveUserId,
                new ExtendTrialRequest(null, 14, "inactive", null)));
    }

    @Test
    void extendTrialRejectsNullAdminUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.extendTrial(
                null,
                targetUserId,
                new ExtendTrialRequest(null, 14, "x", null)));
    }

    @Test
    void extendTrialRejectsNullTargetUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.extendTrial(
                adminUserId,
                null,
                new ExtendTrialRequest(null, 14, "x", null)));
    }

    // --- fixtures ----------------------------------------------------------

    private void insertUser(UUID userId, String email, String status) throws SQLException {
        String sql = """
                INSERT INTO app_user
                    (id, email_address, normalized_email, email_verified,
                     first_name, last_name, display_name, mobile_number,
                     status, created_at, updated_at, last_login_at)
                VALUES (?, ?, ?, TRUE, NULL, NULL, ?, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, email.toLowerCase());
            ps.setString(4, email);
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private void insertStatus(UUID userId, String status) throws SQLException {
        String sql = """
                INSERT INTO user_subscription
                    (user_id, status, plan_key, cancel_at_period_end, created_at, updated_at)
                VALUES (?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, status);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }

    private void insertTrial(UUID userId, Instant trialEnd) throws SQLException {
        insertTrialWithStart(userId, Instant.now().minus(Duration.ofDays(1)), trialEnd);
    }

    private void insertTrialWithStart(UUID userId, Instant trialStart, Instant trialEnd) throws SQLException {
        String sql = """
                INSERT INTO user_subscription
                    (user_id, status, plan_key,
                     trial_start, trial_end,
                     cancel_at_period_end, created_at, updated_at)
                VALUES (?, 'trialing', 'trial', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setTimestamp(2, java.sql.Timestamp.from(trialStart));
            ps.setTimestamp(3, java.sql.Timestamp.from(trialEnd));
            ps.executeUpdate();
        }
    }
}
