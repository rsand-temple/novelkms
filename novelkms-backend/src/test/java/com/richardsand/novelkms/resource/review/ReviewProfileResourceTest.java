package com.richardsand.novelkms.resource.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ReviewMetricsDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.model.review.ReviewProfile;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * The HTTP contract for {@link ReviewProfileResource}.
 *
 * <p>
 * Three behaviors here are load-bearing and easy to regress:
 *
 * <ul>
 * <li><b>204, not 404, for "no profile yet."</b> Having no handle is the normal
 * starting state of every user. A 404 would make the frontend's happy path
 * look like an error.</li>
 * <li><b>400 for a malformed handle, 409 for one that cannot be had.</b>
 * Collapsing both into 409 tells the caller "pick a different handle" when
 * the real answer is "that isn't a handle."</li>
 * <li><b>404, never 403, for hidden/suspended/absent profiles.</b> This is the
 * network's first cross-user read path, and it must not disclose existence —
 * the same rule {@code TenantAuthorizationFilter} already enforces for
 * manuscript entities. The owner, however, always sees their own row.</li>
 * </ul>
 *
 * <p>
 * Two {@link ResourceExtension}s are built, authenticated as two different
 * users. The disclosure rules are only meaningful when someone other than the
 * owner is asking, and the existing resource tests had no pattern for that.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ReviewProfileResourceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao DAO     = new ReviewProfileDao(ds);
    private static final ReviewMetricsDao METRICS = new ReviewMetricsDao(ds);

    /** The profile owner in most tests. */
    static final ResourceExtension AS_OWNER = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(TEST_USER_ID))
            .addResource(new ReviewProfileResource(DAO, METRICS))
            .setMapper(createMapper())
            .build();

    /** A different signed-in user — the one the disclosure rules are aimed at. */
    static final ResourceExtension AS_STRANGER = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(OTHER_USER_ID))
            .addResource(new ReviewProfileResource(DAO, METRICS))
            .setMapper(createMapper())
            .build();

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Map<String, Object> body(String handle) {
        Map<String, Object> b = new HashMap<>();
        b.put("handle", handle);
        return b;
    }

    private static Response post(ResourceExtension as, Map<String, Object> b) {
        return as.target("/review/profile")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(b));
    }

    private static Response put(ResourceExtension as, Map<String, Object> b) {
        return as.target("/review/profile")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(b));
    }

    private static Map<String, Object> asMap(Response r) {
        return r.readEntity(new jakarta.ws.rs.core.GenericType<Map<String, Object>>() {
        });
    }

    private static String errorCode(Response r) {
        return String.valueOf(asMap(r).get("error"));
    }

    // =========================================================================
    // GET /review/profile/me
    // =========================================================================

    @Test
    void me_withNoProfile_returns204NotAnError() {
        Response r = AS_OWNER.target("/review/profile/me").request().get();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus(),
                "no profile yet is the normal starting state, not a 404");
        assertFalse(r.hasEntity());
    }

    @Test
    void me_afterCreate_returnsTheProfile() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Response r = AS_OWNER.target("/review/profile/me").request().get();

        assertEquals(200, r.getStatus());
        assertEquals("Cormac_M", r.readEntity(ReviewProfile.class).getHandle());
    }

    /** One user creating a profile must not conjure one for anybody else. */
    @Test
    void me_isScopedToTheCaller() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Response r = AS_STRANGER.target("/review/profile/me").request().get();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    // =========================================================================
    // POST /review/profile — create
    // =========================================================================

    @Test
    void create_returnsThePersistedProfile() throws SQLException {
        Map<String, Object> b = body("Cormac_M");
        b.put("displayName", "Cormac M.");
        b.put("bio", "Writes bleak westerns.");
        b.put("genresWritten", List.of("literary", "western"));
        b.put("genresReviewed", List.of("literary"));
        b.put("visibility", "PUBLIC");

        Response r = post(AS_OWNER, b);

        assertEquals(200, r.getStatus());
        ReviewProfile created = r.readEntity(ReviewProfile.class);
        assertEquals("Cormac_M", created.getHandle());
        assertEquals(List.of("literary", "western"), created.getGenresWritten());
        assertEquals("ACTIVE", created.getStatus());

        assertTrue(DAO.findByUserId(TEST_USER_ID).isPresent());
    }

    @Test
    void create_twice_conflicts() {
        assertEquals(200, post(AS_OWNER, body("First")).getStatus());

        Response r = post(AS_OWNER, body("Second"));

        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("profile_exists", errorCode(r));
    }

    // ---- Malformed handles are 400 -----------------------------------------

    @Test
    void create_handleTooShort_is400() {
        Response r = post(AS_OWNER, body("ab"));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_invalid", errorCode(r));
    }

    @Test
    void create_handleTooLong_is400() {
        Response r = post(AS_OWNER, body("a".repeat(25)));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_invalid", errorCode(r));
    }

    @Test
    void create_handleStartingWithDigit_is400() {
        Response r = post(AS_OWNER, body("9lives"));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_invalid", errorCode(r));
    }

    @Test
    void create_handleWithIllegalCharacters_is400() {
        for (String bad : List.of("has space", "has-dash", "has.dot", "has@at", "emoji\uD83D\uDE00")) {
            Response r = post(AS_OWNER, body(bad));
            assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus(), "should reject: " + bad);
            assertEquals("handle_invalid", errorCode(r));
        }
    }

    @Test
    void create_missingHandle_is400() {
        Response r = post(AS_OWNER, body(null));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_required", errorCode(r));
    }

    @Test
    void create_blankHandle_is400() {
        Response r = post(AS_OWNER, body("   "));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_required", errorCode(r));
    }

    // ---- Unavailable handles are 409 ---------------------------------------

    @Test
    void create_reservedHandle_is409() {
        for (String reserved : List.of("admin", "Admin", "ADMIN", "novelkms", "support", "moderator")) {
            truncateSilently();

            Response r = post(AS_OWNER, body(reserved));
            assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus(), "should reserve: " + reserved);
            assertEquals("handle_reserved", errorCode(r));
        }
    }

    @Test
    void create_handleTakenByAnotherUser_is409_evenInDifferentCase() throws SQLException {
        DAO.create(OTHER_USER_ID, ReviewProfile.builder().handle("Cormac_M").build());

        Response r = post(AS_OWNER, body("cormac_m"));

        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("handle_taken", errorCode(r));
    }

    // ---- Field validation ---------------------------------------------------

    @Test
    void create_bioTooLong_is400() {
        Map<String, Object> b = body("Verbose");
        b.put("bio", "x".repeat(2001));

        Response r = post(AS_OWNER, b);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("bio_too_long", errorCode(r));
    }

    @Test
    void create_displayNameTooLong_is400() {
        Map<String, Object> b = body("Verbose");
        b.put("displayName", "x".repeat(121));

        Response r = post(AS_OWNER, b);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("display_name_too_long", errorCode(r));
    }

    @Test
    void create_tooManyGenres_is400() {
        Map<String, Object> b = body("Eclectic");
        b.put("genresWritten", List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m"));

        Response r = post(AS_OWNER, b);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("too_many_genres", errorCode(r));
    }

    /**
     * Genres are packed into one comma-separated column. A comma inside a value
     * would silently split it in two on the way back out, so it is rejected at
     * the door rather than corrupted in storage.
     */
    @Test
    void create_genreContainingComma_is400() {
        Map<String, Object> b = body("Sneaky");
        b.put("genresReviewed", List.of("literary, western"));

        Response r = post(AS_OWNER, b);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("genre_invalid", errorCode(r));
    }

    // =========================================================================
    // PUT /review/profile — update
    // =========================================================================

    @Test
    void update_beforeCreate_is404() {
        Response r = put(AS_OWNER, body("Ghost"));

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
        assertEquals("no_profile", errorCode(r));
    }

    /**
     * The self-exclusion regression guard. Saving an unchanged profile must NOT
     * report the user's own handle as taken.
     */
    @Test
    void update_keepingTheSameHandle_succeeds() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Map<String, Object> b = body("Cormac_M");
        b.put("bio", "Revised bio.");

        Response r = put(AS_OWNER, b);

        assertEquals(200, r.getStatus(), "re-saving your own handle must not be a conflict");
        assertEquals("Revised bio.", r.readEntity(ReviewProfile.class).getBio());
    }

    @Test
    void update_changingCaseOfOwnHandle_succeeds() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Response r = put(AS_OWNER, body("CORMAC_M"));

        assertEquals(200, r.getStatus());
        assertEquals("CORMAC_M", r.readEntity(ReviewProfile.class).getHandle());
    }

    @Test
    void update_toAnotherUsersHandle_is409() throws SQLException {
        DAO.create(OTHER_USER_ID, ReviewProfile.builder().handle("Taken").build());
        assertEquals(200, post(AS_OWNER, body("Mine")).getStatus());

        Response r = put(AS_OWNER, body("taken"));

        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("handle_taken", errorCode(r));
    }

    @Test
    void update_toAMalformedHandle_is400() {
        assertEquals(200, post(AS_OWNER, body("Mine")).getStatus());

        Response r = put(AS_OWNER, body("no"));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("handle_invalid", errorCode(r));
    }

    /**
     * Identity cannot be forged through the payload, because {@code ProfileRequest}
     * has no {@code id} or {@code userId} field to forge. The strict mapper rejects
     * the unknown properties outright, so the request never reaches the resource —
     * a 400, not a quietly-dropped field.
     *
     * <p>
     * This documents the DTO contract. It is deliberately NOT the security
     * test — see {@link #update_cannotReachAnotherUsersRow()} for that. Relaxing
     * FAIL_ON_UNKNOWN_PROPERTIES would flip this to 200 without making anything
     * unsafe, so the two concerns are kept apart rather than conflated into one
     * assertion that would raise a false alarm.
     */
    @Test
    void update_forgedIdentityFieldsInPayload_areRejected() throws SQLException {
        ReviewProfile victim = DAO.create(OTHER_USER_ID,
                ReviewProfile.builder().handle("Victim").build());
        assertEquals(200, post(AS_OWNER, body("Attacker")).getStatus());

        Map<String, Object> b = body("Attacker");
        b.put("id", victim.getId().toString());
        b.put("userId", OTHER_USER_ID.toString());
        b.put("bio", "pwned");

        assertEquals(Status.BAD_REQUEST.getStatusCode(), put(AS_OWNER, b).getStatus());

        ReviewProfile untouched = DAO.findByUserId(OTHER_USER_ID).orElseThrow();
        assertEquals("Victim", untouched.getHandle());
        assertNull(untouched.getBio());
    }

    /**
     * The security invariant proper, and it holds no matter how lenient the mapper
     * is: an accepted, entirely legitimate update from one user must land on that
     * user's row and no other. The DAO's UPDATE carries its own
     * {@code WHERE user_id = ?}, so the caller's authenticated identity — not
     * anything in the payload — decides which row moves.
     */
    @Test
    void update_cannotReachAnotherUsersRow() throws SQLException {
        DAO.create(OTHER_USER_ID, ReviewProfile.builder().handle("Victim").build());
        assertEquals(200, post(AS_OWNER, body("Attacker")).getStatus());

        Map<String, Object> b = body("Attacker");
        b.put("bio", "mine alone");
        assertEquals(200, put(AS_OWNER, b).getStatus());

        ReviewProfile victim = DAO.findByUserId(OTHER_USER_ID).orElseThrow();
        assertEquals("Victim", victim.getHandle());
        assertNull(victim.getBio(), "the other user's row must not have moved");

        assertEquals("mine alone", DAO.findByUserId(TEST_USER_ID).orElseThrow().getBio(),
                "the caller's own row must have been the one updated");
    }

    // =========================================================================
    // GET /review/handles/{handle}/available
    // =========================================================================

    @Test
    void available_freeHandle_isAvailable() {
        Map<String, Object> r = asMap(
                AS_OWNER.target("/review/handles/Nobody/available").request().get());

        assertEquals(Boolean.TRUE, r.get("available"));
        assertEquals("", r.get("reason"));
    }

    @Test
    void available_takenHandle_reportsTaken() throws SQLException {
        DAO.create(OTHER_USER_ID, ReviewProfile.builder().handle("Cormac_M").build());

        Map<String, Object> r = asMap(
                AS_OWNER.target("/review/handles/cormac_m/available").request().get());

        assertEquals(Boolean.FALSE, r.get("available"));
        assertEquals("handle_taken", r.get("reason"));
    }

    @Test
    void available_reservedHandle_reportsReserved() {
        Map<String, Object> r = asMap(
                AS_OWNER.target("/review/handles/admin/available").request().get());

        assertEquals(Boolean.FALSE, r.get("available"));
        assertEquals("handle_reserved", r.get("reason"));
    }

    /**
     * A malformed handle is reported as unavailable-with-a-reason, NOT as a 400.
     * This endpoint is a question, not a claim — the form asks it on every
     * keystroke, and a stream of 400s would be noise.
     */
    @Test
    void available_malformedHandle_is200WithReason() {
        Response raw = AS_OWNER.target("/review/handles/9lives/available").request().get();

        assertEquals(200, raw.getStatus());
        Map<String, Object> r = asMap(raw);
        assertEquals(Boolean.FALSE, r.get("available"));
        assertEquals("handle_invalid", r.get("reason"));
    }

    /** The caller's own handle must read as available to them, or the form fights itself. */
    @Test
    void available_ownHandle_isAvailableToTheOwner() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Map<String, Object> mine = asMap(
                AS_OWNER.target("/review/handles/Cormac_M/available").request().get());
        assertEquals(Boolean.TRUE, mine.get("available"));

        Map<String, Object> theirs = asMap(
                AS_STRANGER.target("/review/handles/Cormac_M/available").request().get());
        assertEquals(Boolean.FALSE, theirs.get("available"));
        assertEquals("handle_taken", theirs.get("reason"));
    }

    // =========================================================================
    // GET /review/profiles/{handle} — the first cross-user read
    // =========================================================================

    @Test
    void byHandle_publicProfile_isVisibleToOtherUsers() {
        Map<String, Object> b = body("Cormac_M");
        b.put("displayName", "Cormac M.");
        b.put("bio", "Writes bleak westerns.");
        assertEquals(200, post(AS_OWNER, b).getStatus());

        Response r = AS_STRANGER.target("/review/profiles/cormac_m").request().get();

        assertEquals(200, r.getStatus());
        ReviewProfile seen = r.readEntity(ReviewProfile.class);
        assertEquals("Cormac_M", seen.getHandle());
        assertEquals("Cormac M.", seen.getDisplayName());
    }

    /**
     * The profile is the only user-shaped object other users can read. Nothing
     * from {@code app_user} — above all the email address — may ride along.
     */
    @Test
    void byHandle_neverLeaksAuthenticationIdentity() {
        assertEquals(200, post(AS_OWNER, body("Cormac_M")).getStatus());

        Response            r   = AS_STRANGER.target("/review/profiles/Cormac_M").request().get();
        Map<String, Object> raw = asMap(r);

        assertFalse(raw.containsKey("email"));
        assertFalse(raw.containsKey("emailAddress"));
        assertFalse(raw.containsKey("normalizedEmail"));
        assertFalse(raw.toString().contains("test.user@example.com"));
    }

    @Test
    void byHandle_unknownHandle_is404() {
        Response r = AS_STRANGER.target("/review/profiles/ghost").request().get();

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void byHandle_hiddenProfile_is404ToStrangers_not403() {
        Map<String, Object> b = body("Shy_One");
        b.put("visibility", "HIDDEN");
        assertEquals(200, post(AS_OWNER, b).getStatus());

        Response r = AS_STRANGER.target("/review/profiles/Shy_One").request().get();

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus(),
                "403 would confirm the handle exists; existence must not be disclosed");
    }

    /** ...but the owner can always preview their own hidden profile. */
    @Test
    void byHandle_hiddenProfile_isVisibleToItsOwner() {
        Map<String, Object> b = body("Shy_One");
        b.put("visibility", "HIDDEN");
        assertEquals(200, post(AS_OWNER, b).getStatus());

        Response r = AS_OWNER.target("/review/profiles/Shy_One").request().get();

        assertEquals(200, r.getStatus());
        assertEquals("HIDDEN", r.readEntity(ReviewProfile.class).getVisibility());
    }

    @Test
    void byHandle_suspendedProfile_is404ToStrangers() throws SQLException {
        assertEquals(200, post(AS_OWNER, body("Trouble")).getStatus());
        DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED);

        Response r = AS_STRANGER.target("/review/profiles/Trouble").request().get();

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void byHandle_suspendedProfile_isStillVisibleToItsOwner() throws SQLException {
        assertEquals(200, post(AS_OWNER, body("Trouble")).getStatus());
        DAO.setStatus(TEST_USER_ID, ReviewProfileDao.STATUS_SUSPENDED);

        Response r = AS_OWNER.target("/review/profiles/Trouble").request().get();

        assertEquals(200, r.getStatus(),
                "a suspended user must still be able to see their own profile");
    }

    // =========================================================================
    // Test-local helper
    // =========================================================================

    /**
     * The reserved-handle test loops over several candidates and must start each
     * iteration from a clean slate, since the first successful POST would
     * otherwise trip {@code profile_exists} on the next.
     */
    private static void truncateSilently() {
        try {
            truncateAll();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
