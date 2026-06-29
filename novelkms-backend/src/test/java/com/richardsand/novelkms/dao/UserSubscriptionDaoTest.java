package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.UserSubscription;

public class UserSubscriptionDaoTest extends NovelKmsTestBase {

    private static final Instant NOW       = Instant.parse("2026-06-28T12:00:00Z");
    private static final Instant TRIAL_END = NOW.plusSeconds(14L * 24L * 60L * 60L);

    private UserSubscriptionDao dao;

    @BeforeEach
    void setUp() throws Exception {
        truncateAll();
        dao = new UserSubscriptionDao(ds);
    }

    @Test
    void startTrialInsertsTrialingEntitlement() throws Exception {
        Optional<UserSubscription> created = dao.startTrial(TEST_USER_ID, NOW, TRIAL_END);

        assertTrue(created.isPresent());

        UserSubscription subscription = created.get();
        assertEquals(TEST_USER_ID, subscription.userId());
        assertEquals("trialing", subscription.status());
        assertEquals("trial", subscription.planKey());
        assertEquals(NOW, subscription.trialStart());
        assertEquals(TRIAL_END, subscription.trialEnd());
        assertEquals(NOW, subscription.currentPeriodStart());
        assertEquals(TRIAL_END, subscription.currentPeriodEnd());
        assertFalse(subscription.cancelAtPeriodEnd());
        assertTrue(subscription.hasAccess(NOW));
    }

    @Test
    void startTrialReturnsEmptyWhenSubscriptionAlreadyExists() throws Exception {
        Optional<UserSubscription> first  = dao.startTrial(TEST_USER_ID, NOW, TRIAL_END);
        Optional<UserSubscription> second = dao.startTrial(TEST_USER_ID, NOW.plusSeconds(60), TRIAL_END.plusSeconds(60));

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());

        UserSubscription stored = dao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("trialing", stored.status());
        assertEquals(NOW, stored.trialStart());
        assertEquals(TRIAL_END, stored.trialEnd());
    }

    @Test
    void startTrialDoesNotOverwriteFamilyAccess() throws Exception {
        dao.setFamilyAccess(TEST_USER_ID);

        Optional<UserSubscription> trial = dao.startTrial(TEST_USER_ID, NOW, TRIAL_END);

        assertTrue(trial.isEmpty());

        UserSubscription stored = dao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("family", stored.status());
        assertTrue(stored.isFamilyAccess());
        assertTrue(stored.hasAccess(NOW));
    }

    @Test
    void startTrialDoesNotOverwriteStripeSubscription() throws Exception {
        UserSubscriptionDao.StripeSubscriptionUpdate update = new UserSubscriptionDao.StripeSubscriptionUpdate(
                TEST_USER_ID,
                "cus_test",
                "sub_test",
                "active",
                "standard",
                "price_test",
                "prod_test",
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(30),
                null);

        dao.upsertStripeSubscription(update);

        Optional<UserSubscription> trial = dao.startTrial(TEST_USER_ID, NOW, TRIAL_END);

        assertTrue(trial.isEmpty());

        UserSubscription stored = dao.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("active", stored.status());
        assertEquals("cus_test", stored.stripeCustomerId());
        assertEquals("sub_test", stored.stripeSubscriptionId());
        assertEquals("standard", stored.planKey());
        assertTrue(stored.hasAccess(NOW));
    }

    @Test
    void startTrialRejectsNullUserId() {
        assertThrows(IllegalArgumentException.class, () -> dao.startTrial(null, NOW, TRIAL_END));
    }

    @Test
    void startTrialRejectsNullNow() {
        assertThrows(IllegalArgumentException.class, () -> dao.startTrial(TEST_USER_ID, null, TRIAL_END));
    }

    @Test
    void startTrialRejectsNullTrialEnd() {
        assertThrows(IllegalArgumentException.class, () -> dao.startTrial(TEST_USER_ID, NOW, null));
    }

    @Test
    void startTrialRejectsTrialEndEqualToNow() {
        assertThrows(IllegalArgumentException.class, () -> dao.startTrial(TEST_USER_ID, NOW, NOW));
    }

    @Test
    void startTrialRejectsTrialEndBeforeNow() {
        assertThrows(IllegalArgumentException.class, () -> dao.startTrial(TEST_USER_ID, NOW, NOW.minusSeconds(1)));
    }

    @Test
    void hasAccessReturnsFalseForExpiredTrial() throws Exception {
        dao.startTrial(TEST_USER_ID, NOW.minusSeconds(3600), NOW.minusSeconds(1));

        assertFalse(dao.hasAccess(TEST_USER_ID, NOW));
    }

    @Test
    void markPaymentFailedDoesNotDemoteFamilyAccess() throws Exception {
        dao.setFamilyAccess(TEST_USER_ID);
        attachStripeIds(TEST_USER_ID, "cus_family", "sub_family");

        Optional<UserSubscription> updated = dao.markPaymentFailed("cus_family", "sub_family", NOW);

        assertTrue(updated.isPresent());
        assertEquals("family", updated.get().status());
        assertNotNull(updated.get().lastPaymentFailedAt());
        assertTrue(updated.get().hasAccess(NOW));
    }

    @Test
    void markPaymentFailedSetsPastDueForNonFamilyUser() throws Exception {
        UserSubscriptionDao.StripeSubscriptionUpdate update = new UserSubscriptionDao.StripeSubscriptionUpdate(
                TEST_USER_ID,
                "cus_paid",
                "sub_paid",
                "active",
                "standard",
                "price_test",
                "prod_test",
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(30),
                null);

        dao.upsertStripeSubscription(update);

        Optional<UserSubscription> failed = dao.markPaymentFailed("cus_paid", "sub_paid", NOW);

        assertTrue(failed.isPresent());
        assertEquals("past_due", failed.get().status());
        assertNotNull(failed.get().lastPaymentFailedAt());
        assertTrue(failed.get().hasAccess(NOW));
    }

    @Test
    void upsertStripeSubscriptionPreservesFamilyStatus() throws Exception {
        dao.setFamilyAccess(TEST_USER_ID);

        UserSubscriptionDao.StripeSubscriptionUpdate update = new UserSubscriptionDao.StripeSubscriptionUpdate(
                TEST_USER_ID,
                "cus_family",
                "sub_family",
                "canceled",
                "standard",
                "price_test",
                "prod_test",
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60),
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                NOW.minusSeconds(30),
                null,
                null);

        Optional<UserSubscription> stored = dao.upsertStripeSubscription(update);

        assertTrue(stored.isPresent());
        assertEquals("family", stored.get().status());
        assertEquals("cus_family", stored.get().stripeCustomerId());
        assertEquals("sub_family", stored.get().stripeSubscriptionId());
        assertTrue(stored.get().hasAccess(NOW));
    }

    @Test
    void trialForOneUserDoesNotGrantOtherUserAccess() throws Exception {
        dao.startTrial(TEST_USER_ID, NOW, TRIAL_END);

        assertTrue(dao.hasAccess(TEST_USER_ID, NOW));
        assertFalse(dao.hasAccess(OTHER_USER_ID, NOW));
    }

    private void attachStripeIds(UUID userId, String customerId, String subscriptionId) throws Exception {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        UPDATE user_subscription
                           SET stripe_customer_id = ?,
                               stripe_subscription_id = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE user_id = ?
                        """)) {
            ps.setString(1, customerId);
            ps.setString(2, subscriptionId);
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
    }
}
