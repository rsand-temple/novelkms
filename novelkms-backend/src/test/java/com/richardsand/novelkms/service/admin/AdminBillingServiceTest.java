package com.richardsand.novelkms.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.AdminAuditDao;
import com.richardsand.novelkms.dao.AdminUserDao;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.UserSubscriptionDao;
import com.richardsand.novelkms.model.AdminAuditLogEntry;
import com.richardsand.novelkms.model.UserSubscription;

import jakarta.ws.rs.NotFoundException;

class AdminBillingServiceTest extends NovelKmsTestBase {
    private AdminAuditDao       adminAuditDao;
    private UserSubscriptionDao userSubscriptionDao;
    private AuthDao             authDao;
    private AdminBillingService service;

    private UUID         adminUserId;
    private UUID         targetUserId;
    private UUID         inactiveUserId;
    private AdminUserDao adminUserDao;

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

    @Test
    void grantFamilyAccessCreatesSubscriptionWhenMissing() throws Exception {
        UserSubscription subscription = service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "family_discount",
                "Initial family grant");

        assertEquals(targetUserId, subscription.userId());
        assertEquals("family", subscription.status());
        assertNull(subscription.stripeCustomerId());
        assertNull(subscription.stripeSubscriptionId());
        assertNotNull(subscription.createdAt());
        assertNotNull(subscription.updatedAt());

        UserSubscription persisted = userSubscriptionDao.findByUserId(targetUserId).orElseThrow();
        assertEquals("family", persisted.status());
    }

    @Test
    void grantFamilyAccessUpdatesExistingSubscriptionToFamily() throws Exception {
        insertSubscription(
                targetUserId,
                "trialing",
                "trial",
                "cus_" + targetUserId,
                "sub_" + targetUserId,
                "price_trial",
                "prod_trial");

        UserSubscription subscription = service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "founder_account",
                "Convert trial to family access");

        assertEquals(targetUserId, subscription.userId());
        assertEquals("family", subscription.status());

        /*
         * Existing Stripe metadata should be preserved by setManualStatus(...)
         * because only status and updated_at are changed.
         */
        assertEquals("cus_" + targetUserId, subscription.stripeCustomerId());
        assertEquals("sub_" + targetUserId, subscription.stripeSubscriptionId());
        assertEquals("trial", subscription.planKey());
    }

    @Test
    void grantFamilyAccessWritesAuditRowWhenSubscriptionWasMissing() throws Exception {
        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "family_discount",
                "Initial family grant");

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);

        assertEquals(1, auditRows.size());

        AdminAuditLogEntry audit = auditRows.get(0);
        assertEquals(adminUserId, audit.adminUserId());
        assertEquals(targetUserId, audit.targetUserId());
        assertEquals(AdminBillingService.ACTION_GRANT_FAMILY_ACCESS, audit.action());
        assertEquals(AdminBillingService.ENTITY_USER_SUBSCRIPTION, audit.entityType());
        assertEquals(targetUserId.toString(), audit.entityId());
        assertNull(audit.oldValue());
        assertNotNull(audit.newValue());
        assertTrue(audit.newValue().contains("\"status\":\"family\""));
        assertEquals("family_discount - Initial family grant", audit.reason());
        assertNotNull(audit.createdAt());
    }

    @Test
    void grantFamilyAccessWritesAuditRowWithOldAndNewValuesForExistingSubscription() throws Exception {
        insertSubscription(
                targetUserId,
                "trialing",
                "trial",
                "cus_" + targetUserId,
                "sub_" + targetUserId,
                "price_trial",
                "prod_trial");

        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "founder_account",
                "Convert trial to family access");

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);

        assertEquals(1, auditRows.size());

        AdminAuditLogEntry audit = auditRows.get(0);
        assertNotNull(audit.oldValue());
        assertNotNull(audit.newValue());

        assertTrue(audit.oldValue().contains("\"status\":\"trialing\""));
        assertTrue(audit.oldValue().contains("\"stripeCustomerId\":\"cus_"));
        assertTrue(audit.newValue().contains("\"status\":\"family\""));
        assertTrue(audit.newValue().contains("\"stripeCustomerId\":\"cus_"));
        assertEquals("founder_account - Convert trial to family access", audit.reason());
    }

    @Test
    void grantFamilyAccessUsesDefaultAuditReasonWhenReasonAndNoteAreBlank() throws Exception {
        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                " ",
                null);

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);

        assertEquals(1, auditRows.size());
        assertEquals("Family access granted", auditRows.get(0).reason());
    }

    @Test
    void grantFamilyAccessUsesNoteWhenReasonIsBlank() throws Exception {
        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                null,
                "Manual support comp");

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);

        assertEquals(1, auditRows.size());
        assertEquals("Manual support comp", auditRows.get(0).reason());
    }

    @Test
    void grantFamilyAccessUsesReasonWhenNoteIsBlank() throws Exception {
        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "family_discount",
                " ");

        List<AdminAuditLogEntry> auditRows = adminAuditDao.forTargetUser(targetUserId, 10);

        assertEquals(1, auditRows.size());
        assertEquals("family_discount", auditRows.get(0).reason());
    }

    @Test
    void grantFamilyAccessRejectsNullAdminUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.grantFamilyAccess(
                null,
                targetUserId,
                "family_discount",
                null));
    }

    @Test
    void grantFamilyAccessRejectsNullTargetUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.grantFamilyAccess(
                adminUserId,
                null,
                "family_discount",
                null));
    }

    @Test
    void grantFamilyAccessRejectsUnknownTargetUser() {
        UUID unknownUserId = UUID.randomUUID();

        assertThrows(NotFoundException.class, () -> service.grantFamilyAccess(
                adminUserId,
                unknownUserId,
                "family_discount",
                null));
    }

    @Test
    void grantFamilyAccessRejectsInactiveTargetUser() {
        assertThrows(NotFoundException.class, () -> service.grantFamilyAccess(
                adminUserId,
                inactiveUserId,
                "family_discount",
                null));
    }

    @Test
    void billingDetailReturnsNoSubscriptionStateWhenMissing() throws Exception {
        var detail = service.billingDetail(targetUserId);

        assertEquals(targetUserId, detail.userId());
        assertEquals("ACTIVE", detail.userStatus());
        assertEquals(null, detail.subscription());
        assertEquals(false, detail.hasSubscriptionRow());
        assertEquals(false, detail.hasAccess());
        assertEquals(false, detail.familyAccess());
        assertEquals(false, detail.stripeLinked());
        assertEquals(false, detail.trialActive());
        assertEquals(false, detail.canceling());
        assertEquals(false, detail.paymentProblem());
        assertEquals("none", detail.accessReason());
        assertNotNull(detail.evaluatedAt());
    }

    @Test
    void billingDetailReturnsFamilyAccessState() throws Exception {
        service.grantFamilyAccess(
                adminUserId,
                targetUserId,
                "family_discount",
                "Initial family grant");

        var detail = service.billingDetail(targetUserId);

        assertEquals(targetUserId, detail.userId());
        assertEquals("family", detail.subscription().status());
        assertEquals(true, detail.hasSubscriptionRow());
        assertEquals(true, detail.hasAccess());
        assertEquals(true, detail.familyAccess());
        assertEquals(false, detail.stripeLinked());
        assertEquals(false, detail.trialActive());
        assertEquals(false, detail.canceling());
        assertEquals(false, detail.paymentProblem());
        assertEquals("family", detail.accessReason());
    }

    @Test
    void billingDetailReturnsStripeLinkedTrialState() throws Exception {
        insertSubscription(
                targetUserId,
                "trialing",
                "trial",
                "cus_" + targetUserId,
                "sub_" + targetUserId,
                "price_trial",
                "prod_trial");

        var detail = service.billingDetail(targetUserId);

        assertEquals(targetUserId, detail.userId());
        assertEquals("trialing", detail.subscription().status());
        assertEquals("trial", detail.subscription().planKey());
        assertEquals("cus_" + targetUserId, detail.subscription().stripeCustomerId());
        assertEquals("sub_" + targetUserId, detail.subscription().stripeSubscriptionId());
        assertEquals(true, detail.hasSubscriptionRow());
        assertEquals(true, detail.hasAccess());
        assertEquals(false, detail.familyAccess());
        assertEquals(true, detail.stripeLinked());
        assertEquals(true, detail.trialActive());
        assertEquals(false, detail.canceling());
        assertEquals(false, detail.paymentProblem());
        assertEquals("trial_active", detail.accessReason());
    }

    @Test
    void billingDetailRejectsUnknownTargetUser() {
        assertThrows(NotFoundException.class, () -> service.billingDetail(UUID.randomUUID()));
    }

    @Test
    void billingDetailRejectsNullTargetUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.billingDetail(null));
    }

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

    private void insertSubscription(
            UUID userId,
            String status,
            String planKey,
            String stripeCustomerId,
            String stripeSubscriptionId,
            String stripePriceId,
            String stripeProductId) throws SQLException {

        Instant now       = Instant.now();
        Instant periodEnd = now.plusSeconds(30L * 24L * 60L * 60L);

        String sql = """
                INSERT INTO user_subscription
                    (user_id, stripe_customer_id, stripe_subscription_id, status, plan_key,
                     stripe_price_id, stripe_product_id,
                     current_period_start, current_period_end, trial_start, trial_end,
                     cancel_at_period_end,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, stripeCustomerId);
            ps.setString(3, stripeSubscriptionId);
            ps.setString(4, status);
            ps.setString(5, planKey);
            ps.setString(6, stripePriceId);
            ps.setString(7, stripeProductId);
            ps.setTimestamp(8, java.sql.Timestamp.from(now));
            ps.setTimestamp(9, java.sql.Timestamp.from(periodEnd));
            ps.setTimestamp(10, java.sql.Timestamp.from(now));
            ps.setTimestamp(11, java.sql.Timestamp.from(periodEnd));
            ps.executeUpdate();
        }
    }
}