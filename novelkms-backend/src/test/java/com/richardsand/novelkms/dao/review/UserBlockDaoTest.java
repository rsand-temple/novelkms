package com.richardsand.novelkms.dao.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.review.BlockedUser;
import com.richardsand.novelkms.model.review.ReviewProfile;

/**
 * The write side of {@code user_block} (slice 1F): blocking is idempotent, symmetric
 * in effect, scoped to the blocker, and lists back by handle only.
 */
class UserBlockDaoTest extends NovelKmsTestBase {

    private static final ReviewProfileDao PROFILES = new ReviewProfileDao(ds);
    private static final UserBlockDao     BLOCKS   = new UserBlockDao(ds);

    private static final UUID A = TEST_USER_ID;
    private static final UUID B = OTHER_USER_ID;
    private static final UUID C = THIRD_USER_ID;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(A, ReviewProfile.builder().handle("Alpha_One").displayName("Alpha").build());
        PROFILES.create(B, ReviewProfile.builder().handle("Beta_Two").displayName("Beta").build());
        PROFILES.create(C, ReviewProfile.builder().handle("Gamma_Three").build());
    }

    @Test
    void block_isDirectionalInTheRow_butSymmetricInEffect() throws SQLException {
        assertTrue(BLOCKS.block(A, B));

        assertTrue(BLOCKS.isBlocked(A, B), "A recorded a block of B");
        assertFalse(BLOCKS.isBlocked(B, A), "the row itself is one-directional");

        assertTrue(BLOCKS.blockedBetween(A, B), "the read effect is symmetric");
        assertTrue(BLOCKS.blockedBetween(B, A), "...regardless of argument order");
    }

    @Test
    void block_isIdempotent() throws SQLException {
        assertTrue(BLOCKS.block(A, B));
        assertTrue(BLOCKS.block(A, B), "blocking an already-blocked user still succeeds");

        assertEquals(1, BLOCKS.listBlocked(A).size(), "no duplicate row");
    }

    @Test
    void unblock_removesTheRow_andReportsWhetherOneExisted() throws SQLException {
        BLOCKS.block(A, B);

        assertTrue(BLOCKS.unblock(A, B), "an existing block is removed");
        assertFalse(BLOCKS.isBlocked(A, B));
        assertFalse(BLOCKS.blockedBetween(A, B));

        assertFalse(BLOCKS.unblock(A, B), "removing a non-existent block reports false");
    }

    @Test
    void unblock_isScopedToBlocker() throws SQLException {
        BLOCKS.block(A, B);

        // C trying to lift A's block touches nothing.
        assertFalse(BLOCKS.unblock(C, B));
        assertTrue(BLOCKS.isBlocked(A, B), "A's block survives an unrelated user's unblock");
    }

    @Test
    void listBlocked_returnsHandlesForTheCallerOnly() throws SQLException {
        BLOCKS.block(A, B);
        BLOCKS.block(A, C);

        List<BlockedUser> mine = BLOCKS.listBlocked(A);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().anyMatch(u -> "Beta_Two".equals(u.handle())));
        assertTrue(mine.stream().anyMatch(u -> "Gamma_Three".equals(u.handle())));

        assertTrue(BLOCKS.listBlocked(B).isEmpty(), "B has blocked no one");
    }
}
