package com.richardsand.novelkms.dao.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.review.ReviewProfile;

/**
 * {@link ReviewProfileDao} owns three rules the schema alone cannot state, and
 * each one has a failure mode that would be invisible until a user hit it:
 *
 * <ul>
 *   <li><b>Handle casing.</b> Display casing is preserved, uniqueness is
 *       case-insensitive. Get this wrong and {@code @Cormac} and {@code @cormac}
 *       become two different people.</li>
 *   <li><b>Genre packing.</b> A list on the wire, one comma-separated column in
 *       the DB. Get this wrong and genres silently multiply or vanish across a
 *       round trip.</li>
 *   <li><b>User scoping.</b> Updates carry their own {@code WHERE user_id = ?},
 *       so a forged payload cannot reach another user's row even if a caller
 *       forgets to check.</li>
 * </ul>
 *
 * <p>Reads deliberately do NOT filter on visibility or status — that is a
 * disclosure decision belonging to the resource, and the tests below pin the DAO
 * to returning hidden and suspended rows so the resource always has something to
 * decide about.
 */
class ReviewProfileDaoTest extends NovelKmsTestBase {

    private static final ReviewProfileDao DAO = new ReviewProfileDao(ds);

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    private static ReviewProfile profile(String handle) {
        return ReviewProfile.builder().handle(handle).build();
    }

    // =========================================================================
    // Create / read
    // =========================================================================

    @Test
    void create_roundTripsEveryField() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Cormac_M")
                .displayName("Cormac M.")
                .bio("Writes bleak westerns.")
                .genresWritten(List.of("literary", "western"))
                .genresReviewed(List.of("literary"))
                .visibility(ReviewProfileDao.VISIBILITY_PUBLIC)
                .build());

        assertNotNull(created.getId());
        assertEquals(TEST_USER_ID, created.getUserId());
        assertEquals("Cormac_M", created.getHandle());
        assertEquals("Cormac M.", created.getDisplayName());
        assertEquals("Writes bleak westerns.", created.getBio());
        assertEquals(List.of("literary", "western"), created.getGenresWritten());
        assertEquals(List.of("literary"), created.getGenresReviewed());
        assertEquals(ReviewProfileDao.VISIBILITY_PUBLIC, created.getVisibility());
        assertEquals(ReviewProfileDao.STATUS_ACTIVE, created.getStatus());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());

        ReviewProfile reread = DAO.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals(created.getId(), reread.getId());
        assertEquals(List.of("literary", "western"), reread.getGenresWritten());
    }

    @Test
    void findByUserId_absent_isEmpty() throws SQLException {
        assertTrue(DAO.findByUserId(TEST_USER_ID).isEmpty());
    }

    @Test
    void create_defaultsVisibilityToPublicAndStatusToActive() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, profile("Nobody"));

        assertEquals(ReviewProfileDao.VISIBILITY_PUBLIC, created.getVisibility());
        assertEquals(ReviewProfileDao.STATUS_ACTIVE, created.getStatus());
    }

    @Test
    void create_honorsHiddenVisibility() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Shy_One")
                .visibility(ReviewProfileDao.VISIBILITY_HIDDEN)
                .build());

        assertEquals(ReviewProfileDao.VISIBILITY_HIDDEN, created.getVisibility());
    }

    /** An unrecognized visibility must not be persisted verbatim — it falls back to PUBLIC. */
    @Test
    void create_unknownVisibility_fallsBackToPublic() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Weird")
                .visibility("BANANA")
                .build());

        assertEquals(ReviewProfileDao.VISIBILITY_PUBLIC, created.getVisibility());
    }

    // =========================================================================
    // Handle casing
    // =========================================================================

    @Test
    void findByHandle_isCaseInsensitiveButPreservesDisplayCasing() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Cormac_M"));

        for (String lookup : List.of("Cormac_M", "cormac_m", "CORMAC_M", "cOrMaC_m")) {
            ReviewProfile found = DAO.findByHandle(lookup).orElseThrow(
                    () -> new AssertionError("handle lookup failed for casing: " + lookup));
            assertEquals("Cormac_M", found.getHandle(), "display casing must be preserved");
        }
    }

    @Test
    void findByHandle_untrimmedInput_stillResolves() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Cormac_M"));

        assertTrue(DAO.findByHandle("  cormac_m  ").isPresent());
    }

    @Test
    void findByHandle_unknown_isEmpty() throws SQLException {
        assertTrue(DAO.findByHandle("ghost").isEmpty());
    }

    @Test
    void findByHandle_nullOrBlank_isEmpty() throws SQLException {
        assertTrue(DAO.findByHandle(null).isEmpty());
        assertTrue(DAO.findByHandle("   ").isEmpty());
    }

    /**
     * The DB unique index — not the DAO pre-check — is the real arbiter. This is
     * what makes the concurrent-claim race safe.
     */
    @Test
    void create_duplicateHandleDifferingOnlyInCase_violatesUniqueIndex() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Cormac_M"));

        assertThrows(SQLException.class,
                () -> DAO.create(OTHER_USER_ID, profile("cormac_m")));
    }

    @Test
    void create_secondProfileForSameUser_violatesUniqueUserIndex() throws SQLException {
        DAO.create(TEST_USER_ID, profile("First"));

        assertThrows(SQLException.class,
                () -> DAO.create(TEST_USER_ID, profile("Second")));
    }

    // =========================================================================
    // handleTaken
    // =========================================================================

    @Test
    void handleTaken_unclaimedHandle_isFalse() throws SQLException {
        assertFalse(DAO.handleTaken("Nobody", null));
    }

    @Test
    void handleTaken_claimedByAnotherUser_isTrueRegardlessOfCasing() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Cormac_M"));

        assertTrue(DAO.handleTaken("cormac_m", OTHER_USER_ID));
        assertTrue(DAO.handleTaken("CORMAC_M", OTHER_USER_ID));
        assertTrue(DAO.handleTaken("Cormac_M", null));
    }

    /**
     * The self-exclusion. Without it, a user re-saving an otherwise-unchanged
     * profile would be told their own handle is taken — the single most likely
     * regression in this class.
     */
    @Test
    void handleTaken_claimedByTheExcludedUser_isFalse() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Cormac_M"));

        assertFalse(DAO.handleTaken("Cormac_M", TEST_USER_ID));
        assertFalse(DAO.handleTaken("cormac_m", TEST_USER_ID));
    }

    // =========================================================================
    // Genre packing
    // =========================================================================

    @Test
    void genres_absent_readBackAsEmptyListNotNull() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, profile("Plain"));

        assertEquals(List.of(), created.getGenresWritten());
        assertEquals(List.of(), created.getGenresReviewed());
    }

    @Test
    void genres_emptyList_readBackAsEmptyList() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Plain")
                .genresWritten(List.of())
                .build());

        assertEquals(List.of(), created.getGenresWritten());
    }

    @Test
    void genres_blanksAndDuplicatesAreDropped() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Messy")
                .genresWritten(List.of("literary", "  ", "literary", " western "))
                .build());

        assertEquals(List.of("literary", "western"), created.getGenresWritten());
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Test
    void update_changesFieldsAndKeepsIdentity() throws SQLException {
        ReviewProfile created = DAO.create(TEST_USER_ID, profile("Before"));

        ReviewProfile updated = DAO.update(TEST_USER_ID, ReviewProfile.builder()
                .handle("After")
                .displayName("New Name")
                .bio("New bio.")
                .genresWritten(List.of("horror"))
                .visibility(ReviewProfileDao.VISIBILITY_HIDDEN)
                .build())
                .orElseThrow();

        assertEquals(created.getId(), updated.getId(), "update must not create a new row");
        assertEquals("After", updated.getHandle());
        assertEquals("New Name", updated.getDisplayName());
        assertEquals("New bio.", updated.getBio());
        assertEquals(List.of("horror"), updated.getGenresWritten());
        assertEquals(ReviewProfileDao.VISIBILITY_HIDDEN, updated.getVisibility());
    }

    /** The old handle must actually be freed, not merely shadowed. */
    @Test
    void update_renamingHandle_freesTheOldOne() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Before"));
        DAO.update(TEST_USER_ID, profile("After")).orElseThrow();

        assertTrue(DAO.findByHandle("Before").isEmpty());
        assertTrue(DAO.findByHandle("After").isPresent());
        assertFalse(DAO.handleTaken("Before", null));

        // ...and is genuinely reclaimable by someone else.
        DAO.create(OTHER_USER_ID, profile("before"));
        assertEquals(OTHER_USER_ID, DAO.findByHandle("Before").orElseThrow().getUserId());
    }

    @Test
    void update_clearsOptionalFieldsWhenBlank() throws SQLException {
        DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Somebody")
                .displayName("Somebody")
                .bio("A bio.")
                .genresWritten(List.of("literary"))
                .build());

        ReviewProfile updated = DAO.update(TEST_USER_ID, ReviewProfile.builder()
                .handle("Somebody")
                .displayName("   ")
                .bio("")
                .genresWritten(List.of())
                .build())
                .orElseThrow();

        assertEquals(null, updated.getDisplayName());
        assertEquals(null, updated.getBio());
        assertEquals(List.of(), updated.getGenresWritten());
    }

    @Test
    void update_userWithoutProfile_isEmpty() throws SQLException {
        assertTrue(DAO.update(TEST_USER_ID, profile("Ghost")).isEmpty());
    }

    /**
     * Tenant scoping. The DAO's UPDATE carries its own {@code WHERE user_id = ?},
     * so operating as one user can never reach another user's row.
     */
    @Test
    void update_isScopedToTheCallingUser() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Owner"));

        assertTrue(DAO.update(OTHER_USER_ID, profile("Hijacked")).isEmpty());

        ReviewProfile untouched = DAO.findByUserId(TEST_USER_ID).orElseThrow();
        assertEquals("Owner", untouched.getHandle());
    }

    // =========================================================================
    // Moderation status
    // =========================================================================

    @Test
    void setStatus_suspendsAndReinstates() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Trouble"));

        assertTrue(DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED));
        assertEquals(ReviewProfileDao.STATUS_SUSPENDED,
                DAO.findByUserId(TEST_USER_ID).orElseThrow().getStatus());

        assertTrue(DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_ACTIVE));
        assertEquals(ReviewProfileDao.STATUS_ACTIVE,
                DAO.findByUserId(TEST_USER_ID).orElseThrow().getStatus());
    }

    @Test
    void setStatus_userWithoutProfile_isFalse() throws SQLException {
        assertFalse(DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED));
    }

    /**
     * Suspension is not a filter. The DAO keeps returning the row so the resource
     * can decide what to disclose — a suspended user must still be able to see
     * their own profile, and an admin must still be able to find it.
     */
    @Test
    void update_doesNotResetStatus() throws SQLException {
        DAO.create(TEST_USER_ID, profile("Trouble"));
        DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED);

        ReviewProfile updated = DAO.update(TEST_USER_ID, profile("Trouble2")).orElseThrow();

        assertEquals(ReviewProfileDao.STATUS_SUSPENDED, updated.getStatus(),
                "a user must not be able to un-suspend themselves by saving their profile");
    }

    @Test
    void findByHandle_returnsHiddenAndSuspendedRows() throws SQLException {
        DAO.create(TEST_USER_ID, ReviewProfile.builder()
                .handle("Hidden_One")
                .visibility(ReviewProfileDao.VISIBILITY_HIDDEN)
                .build());
        DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED);

        Optional<ReviewProfile> found = DAO.findByHandle("hidden_one");

        assertTrue(found.isPresent(), "the DAO must not filter on visibility or status");
        assertEquals(ReviewProfileDao.VISIBILITY_HIDDEN, found.get().getVisibility());
        assertEquals(ReviewProfileDao.STATUS_SUSPENDED, found.get().getStatus());
    }

    // =========================================================================
    // Bulk lookup — the queue and review lists resolve handles this way
    // =========================================================================

    @Test
    void findByUserIds_returnsOnlyExistingProfiles() throws SQLException {
        DAO.create(TEST_USER_ID, profile("One"));
        DAO.create(OTHER_USER_ID, profile("Two"));
        // THIRD_USER_ID deliberately has no profile.

        List<ReviewProfile> found = DAO.findByUserIds(
                List.of(TEST_USER_ID, OTHER_USER_ID, THIRD_USER_ID));

        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(p -> p.getHandle().equals("One")));
        assertTrue(found.stream().anyMatch(p -> p.getHandle().equals("Two")));
    }

    @Test
    void findByUserIds_emptyOrNullInput_returnsEmptyList() throws SQLException {
        assertEquals(List.of(), DAO.findByUserIds(List.of()));
        assertEquals(List.of(), DAO.findByUserIds(null));
    }

    // =========================================================================
    // Normalization helper
    // =========================================================================

    @Test
    void normalize_lowercasesAndTrims() {
        assertEquals("cormac_m", ReviewProfileDao.normalize("  Cormac_M "));
        assertEquals(null, ReviewProfileDao.normalize(null));
    }
}
