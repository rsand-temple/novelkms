package com.richardsand.novelkms.resource.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ContentReportDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.review.ReviewProfile;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * The user-facing safety surface (slice 1F): blocking by handle, unblocking
 * idempotently, and filing reports — each gated on holding an active profile,
 * each done by handle rather than user id.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ReviewSafetyResourceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao PROFILES = new ReviewProfileDao(ds);
    private static final UserBlockDao     BLOCKS   = new UserBlockDao(ds);
    private static final ContentReportDao REPORTS  = new ContentReportDao(ds);

    private static ResourceExtension as(java.util.UUID userId) {
        return ResourceExtension.builder()
                .addProvider(testAuthenticationFilter(userId))
                .addResource(new ReviewSafetyResource(BLOCKS, PROFILES, REPORTS))
                .setMapper(createMapper())
                .build();
    }

    /** Has a profile (the reviewer, doing the blocking/reporting). */
    static final ResourceExtension AS_REVIEWER = as(OTHER_USER_ID);
    /** Also has a profile (the author, the target of blocks/reports). */
    static final ResourceExtension AS_AUTHOR = as(TEST_USER_ID);
    /** Has no profile — the participation gate must reject them. */
    static final ResourceExtension AS_NOPROFILE = as(THIRD_USER_ID);

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(TEST_USER_ID,  ReviewProfile.builder().handle("Author_One").displayName("Ann").build());
        PROFILES.create(OTHER_USER_ID, ReviewProfile.builder().handle("Reviewer_Two").build());
        // THIRD_USER_ID intentionally has no profile.
    }

    private static Response postBlock(ResourceExtension as, String handle) {
        return as.target("/review/blocks").request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("handle", handle)));
    }

    private static Response postReport(ResourceExtension as, Map<String, Object> body) {
        return as.target("/review/reports").request(MediaType.APPLICATION_JSON)
                .post(Entity.json(body));
    }

    // =========================================================================
    // Blocking
    // =========================================================================

    @Test
    void block_thenListBlocks_returnsHandle() {
        assertEquals(Status.NO_CONTENT.getStatusCode(), postBlock(AS_REVIEWER, "Author_One").getStatus());

        Response list = AS_REVIEWER.target("/review/blocks").request().get();
        assertEquals(200, list.getStatus());
        List<?> blocks = list.readEntity(List.class);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).toString().contains("Author_One"));
    }

    @Test
    void block_self_is400() {
        assertEquals(Status.BAD_REQUEST.getStatusCode(), postBlock(AS_REVIEWER, "Reviewer_Two").getStatus());
    }

    @Test
    void block_unknownHandle_is404() {
        assertEquals(Status.NOT_FOUND.getStatusCode(), postBlock(AS_REVIEWER, "Ghost_Nobody").getStatus());
    }

    @Test
    void block_withoutProfile_is409() {
        assertEquals(409, postBlock(AS_NOPROFILE, "Author_One").getStatus());
    }

    @Test
    void unblock_isIdempotent() {
        assertEquals(204, postBlock(AS_REVIEWER, "Author_One").getStatus());

        assertEquals(204, AS_REVIEWER.target("/review/blocks/Author_One").request().delete().getStatus());
        // Unblocking again, or a handle that no longer resolves, still succeeds.
        assertEquals(204, AS_REVIEWER.target("/review/blocks/Author_One").request().delete().getStatus());

        Response list = AS_REVIEWER.target("/review/blocks").request().get();
        assertEquals(0, list.readEntity(List.class).size());
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    @Test
    void report_request_isFiledOpen() {
        Response r = postReport(AS_REVIEWER, Map.of(
                "targetType", "REQUEST",
                "targetId", java.util.UUID.randomUUID().toString(),
                "reason", "SPAM"));

        assertEquals(200, r.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = r.readEntity(Map.class);
        assertEquals("OPEN", body.get("status"));
    }

    @Test
    void report_profileByHandle_resolvesTarget() {
        Response r = postReport(AS_REVIEWER, Map.of(
                "targetType", "PROFILE",
                "targetHandle", "Author_One",
                "reason", "HARASSMENT"));

        assertEquals(200, r.getStatus());
    }

    @Test
    void report_invalidReason_is400() {
        Response r = postReport(AS_REVIEWER, Map.of(
                "targetType", "REQUEST",
                "targetId", java.util.UUID.randomUUID().toString(),
                "reason", "NOPE"));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void report_withoutProfile_is409() {
        Response r = postReport(AS_NOPROFILE, Map.of(
                "targetType", "REQUEST",
                "targetId", java.util.UUID.randomUUID().toString(),
                "reason", "SPAM"));

        assertEquals(409, r.getStatus());
    }
}
