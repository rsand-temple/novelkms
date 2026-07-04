package com.richardsand.novelkms.service.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.admin.AdminUserDao;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.model.UserSubscription;
import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.service.ArtifactStorage;
import com.stripe.Stripe;
import com.stripe.model.Subscription;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Irreversible hard-delete of a user account and all associated data.
 *
 * <p>This service is intentionally narrow — it handles one admin mutation that
 * cannot be undone. Every call is written to {@code admin_audit_log} before
 * deletion begins so there is always a record of who ordered the removal and
 * why.
 */
public class AdminUserDeleteService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserDeleteService.class);

    public static final String ACTION_HARD_DELETE_USER = "HARD_DELETE_USER";
    public static final String ENTITY_APP_USER         = "app_user";

    private final DataSource          ds;
    private final AdminUserDao        adminUserDao;
    private final AdminAuditDao       adminAuditDao;
    private final UserSubscriptionDao userSubscriptionDao;
    private final ArtifactStorage     artifactStorage;
    private final NovelKmsConfig      config;
    private final ObjectMapper        objectMapper;

    public AdminUserDeleteService(
            DataSource ds,
            AdminUserDao adminUserDao,
            AdminAuditDao adminAuditDao,
            UserSubscriptionDao userSubscriptionDao,
            ArtifactStorage artifactStorage,
            NovelKmsConfig config,
            ObjectMapper objectMapper) {

        this.ds                  = ds;
        this.adminUserDao        = adminUserDao;
        this.adminAuditDao       = adminAuditDao;
        this.userSubscriptionDao = userSubscriptionDao;
        this.artifactStorage     = artifactStorage;
        this.config              = config;
        this.objectMapper        = objectMapper;
    }

    /**
     * Permanently removes a user and all their data from the database and the
     * artifact blob store. Also attempts to cancel any active Stripe subscription.
     *
     * <p><strong>Guards</strong> (checked before any side effects):
     * <ul>
     *   <li>Admin cannot delete their own account.</li>
     *   <li>Target must not hold the {@code ADMIN} role — the
     *       {@code admin_audit_log.admin_user_id ON DELETE RESTRICT} constraint
     *       would also block it at the DB level, but we check early for a clear
     *       error message.</li>
     * </ul>
     *
     * <p><strong>Order of operations</strong>:
     * <ol>
     *   <li>Collect artifact blob storage keys before any DB changes.</li>
     *   <li>Cancel Stripe subscription (non-fatal on failure).</li>
     *   <li>Write audit log entry — must happen before DELETE because
     *       {@code admin_audit_log.admin_user_id} has {@code ON DELETE RESTRICT}.
     *       The {@code target_user_id} column has {@code ON DELETE SET NULL} and
     *       will become {@code NULL} after deletion, which is expected; the
     *       identity information is preserved in {@code old_value}.</li>
     *   <li>{@code DELETE FROM project WHERE owner_user_id = ?} — {@code project
     *       .owner_user_id} has no {@code ON DELETE CASCADE}, so this must be
     *       explicit. Cascades to books, parts, chapters, scenes, codex, ai_review,
     *       page_layout (book/project scope), editor_settings (book/project scope),
     *       artifact_node subtrees, artifact_blob rows, etc.</li>
     *   <li>{@code DELETE FROM app_user WHERE id = ?} — cascades all remaining
     *       user-scoped rows: user_identity, user_session, trash_batch,
     *       ai_credential, ai_form_global, user_subscription, user_role,
     *       editor_settings (user scope), ui_preferences, template (USER rows),
     *       style (USER rows), remaining artifact_blob rows.</li>
     *   <li>Delete artifact blobs from disk after DB commit.</li>
     * </ol>
     */
    public void hardDelete(UUID adminUserId, UUID targetUserId, String reason) throws Exception {
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId is required");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        // Guard: cannot delete yourself
        if (adminUserId.equals(targetUserId)) {
            throw new BadRequestException("An admin cannot delete their own account.");
        }

        // Look up target — fail fast before touching anything else
        AdminUserDetail target = adminUserDao.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        // Guard: cannot delete another admin account
        if (target.roles() != null && target.roles().contains("ADMIN")) {
            throw new BadRequestException(
                    "Admin accounts cannot be hard-deleted through this interface. "
                    + "Remove the ADMIN role first.");
        }

        // Collect artifact storage keys before any DB deletion
        List<String> storageKeys = collectStorageKeys(targetUserId);

        // Attempt to cancel Stripe subscription — non-fatal
        UserSubscription subscription     = userSubscriptionDao.findByUserId(targetUserId).orElse(null);
        String           canceledStripeId = null;
        if (subscription != null && blankToNull(subscription.stripeSubscriptionId()) != null) {
            canceledStripeId = cancelStripeSubscription(subscription.stripeSubscriptionId());
        }

        // Build audit old_value now — user details will be gone after deletion
        String oldValue = buildOldValue(target, subscription, canceledStripeId);

        // Write audit log entry BEFORE deleting the user row
        adminAuditDao.record(
                adminUserId,
                targetUserId,
                ACTION_HARD_DELETE_USER,
                ENTITY_APP_USER,
                targetUserId.toString(),
                oldValue,
                null,
                blankToNull(reason) != null ? reason.trim() : "Hard delete requested by admin");

        logger.warn("Hard-deleting user: adminUserId={}, targetUserId={}, email={}",
                adminUserId, targetUserId, target.emailAddress());

        deleteFromDb(targetUserId);

        logger.warn("Hard delete DB complete: targetUserId={}", targetUserId);

        // Delete artifact blobs from disk after DB commit
        int blobCount = 0;
        for (String key : storageKeys) {
            artifactStorage.delete(key);
            blobCount++;
        }

        logger.info("Hard delete artifact cleanup complete: targetUserId={}, blobCount={}",
                targetUserId, blobCount);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> collectStorageKeys(UUID userId) throws SQLException {
        List<String> keys = new ArrayList<>();
        String sql = "SELECT storage_key FROM artifact_blob WHERE user_id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("storage_key");
                    if (key != null && !key.isBlank()) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    /**
     * Cancels the Stripe subscription immediately. Returns the subscription ID on
     * success, or {@code null} if billing is not configured or cancellation fails.
     */
    private String cancelStripeSubscription(String stripeSubscriptionId) {
        NovelKmsConfig.Billing billing = config.getBilling();
        if (billing == null || blankToNull(billing.stripeSecretKey) == null) {
            logger.warn("Stripe not configured — skipping subscription cancellation for {}",
                    stripeSubscriptionId);
            return null;
        }
        try {
            Stripe.apiKey = billing.stripeSecretKey;
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            sub.cancel();
            logger.info("Stripe subscription canceled for hard-deleted user: {}",
                    stripeSubscriptionId);
            return stripeSubscriptionId;
        } catch (Exception e) {
            logger.error(
                    "Could not cancel Stripe subscription {} during hard delete — "
                    + "proceeding with local deletion: {}",
                    stripeSubscriptionId, e.getMessage(), e);
            return null;
        }
    }

    private void deleteFromDb(UUID targetUserId) throws SQLException {
        // Step 1: remove all projects — owner_user_id has no ON DELETE CASCADE
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM project WHERE owner_user_id = ?")) {
            ps.setObject(1, targetUserId);
            int count = ps.executeUpdate();
            logger.debug("Deleted {} project(s) for user {}", count, targetUserId);
        }

        // Step 2: remove the user row — everything else cascades
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM app_user WHERE id = ?")) {
            ps.setObject(1, targetUserId);
            int count = ps.executeUpdate();
            if (count == 0) {
                logger.warn("Hard delete: app_user row for {} was already gone", targetUserId);
            }
        }
    }

    /**
     * Captures identifying information in JSON for the audit record. This is
     * important because once deletion completes the {@code target_user_id} column
     * on the audit row becomes {@code NULL} (schema: {@code ON DELETE SET NULL}),
     * so the email / display name preserved here become the only human-readable
     * reference.
     */
    private String buildOldValue(AdminUserDetail user, UserSubscription subscription,
            String canceledStripeSubscriptionId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",           user.id());
        map.put("emailAddress", user.emailAddress());
        map.put("displayName",  user.displayName());
        map.put("status",       user.status());
        map.put("roles",        user.roles());
        map.put("createdAt",    user.createdAt());
        map.put("lastLoginAt",  user.lastLoginAt());
        if (subscription != null) {
            map.put("subscriptionStatus",   subscription.status());
            map.put("stripeCustomerId",     subscription.stripeCustomerId());
            map.put("stripeSubscriptionId", subscription.stripeSubscriptionId());
        }
        if (canceledStripeSubscriptionId != null) {
            map.put("stripeCanceled", canceledStripeSubscriptionId);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return String.valueOf(user);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
