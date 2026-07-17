package com.richardsand.novelkms.resource.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewQueueDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.ReviewAccessService;
import com.richardsand.novelkms.service.ReviewPublishService;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * The HTTP contract for {@link ReviewQueueResource}. The access logic itself lives in {@code ReviewAccessServiceTest}; what is tested here is the wiring — URL
 * shapes, status codes, and above all <b>what does and does not appear on the wire</b>.
 *
 * <p>
 * That last point is why this test exists and not just the service one. The queue and package are the first cross-user reads in NovelKMS, so the serialization
 * is a security boundary: a queue entry must never carry {@code authorUserId} or {@code sourceEntityId}, the package must never carry the frozen text, and the
 * snapshot must never carry {@code sourceEntityId}. A DTO field slipping back onto the wire is a leak a service test cannot catch.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ReviewQueueResourceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao     PROFILES      = new ReviewProfileDao(ds);
    private static final ReviewRequestDao     REQUESTS      = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao    SNAPSHOTS     = new ReviewSnapshotDao(ds);
    private static final ReviewQueueDao       QUEUE         = new ReviewQueueDao(ds);
    private static final UserBlockDao         BLOCKS        = new UserBlockDao(ds);

    private static final ReviewPublishService PUBLISH       = new ReviewPublishService(ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final ReviewAccessService  ACCESS        = new ReviewAccessService(REQUESTS, SNAPSHOTS, PROFILES, QUEUE, BLOCKS);

    static final ResourceExtension            AS_REVIEWER   = ResourceExtension.builder()
                                                                               .addProvider(testAuthenticationFilter(OTHER_USER_ID))
                                                                               .addResource(new ReviewQueueResource(ACCESS))
                                                                               .setMapper(createMapper())
                                                                               .build();

    static final ResourceExtension            AS_AUTHOR     = ResourceExtension.builder()
                                                                               .addProvider(testAuthenticationFilter(TEST_USER_ID))
                                                                               .addResource(new ReviewQueueResource(ACCESS))
                                                                               .setMapper(createMapper())
                                                                               .build();

    // THIRD_USER never claims a handle — the un-profiled caller.
    static final ResourceExtension            AS_NO_PROFILE = ResourceExtension.builder()
                                                                               .addProvider(testAuthenticationFilter(THIRD_USER_ID))
                                                                               .addResource(new ReviewQueueResource(ACCESS))
                                                                               .setMapper(createMapper())
                                                                               .build();

    private ReviewRequest                     request;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();

        PROFILES.create(TEST_USER_ID, ReviewProfile.builder().handle("Author_One").displayName("Ann Author").build());
        PROFILES.create(OTHER_USER_ID, ReviewProfile.builder().handle("Reviewer_Two").build());

        Project project = createTestProject("The Long Road", null);
        Book book = bookDao.create(project.getId(), "Book One", null, null, null);
        Chapter chapter = chapterDao.create(book.getId(), null, "The Crossing", null, null);
        UUID sceneId = sceneDao.create(chapter.getId(), "Scene", null).getId();
        sceneDao.saveContent(sceneId, "<p>One two three four.</p>", 0);

        request = PUBLISH.publishChapter(TEST_USER_ID, chapter.getId(), ReviewRequest.builder().title("A package").genre("fantasy").build());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<Map<String, Object>> list(Response r) {
        return r.readEntity(new GenericType<List<Map<String, Object>>>() {
        });
    }

    private static Map<String, Object> object(Response r) {
        return r.readEntity(new GenericType<Map<String, Object>>() {
        });
    }

    private static Response get(ResourceExtension as, String path) {
        return as.target(path).request(MediaType.APPLICATION_JSON).get();
    }

    // =========================================================================
    // Queue
    // =========================================================================

    @Test
    void queue_asReviewer_returnsTheEntry() {
        Response r = get(AS_REVIEWER, "/review/queue");
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        List<Map<String, Object>> entries = list(r);
        assertEquals(1, entries.size());
        assertEquals("A package", entries.get(0).get("title"));
        assertEquals("Author_One", entries.get(0).get("authorHandle"));
    }

    /** A queue entry exposes the author's handle and nothing that identifies the manuscript. */
    @Test
    void queue_entry_neverLeaksUserIdOrSourceEntity() {
        Map<String, Object> entry = list(get(AS_REVIEWER, "/review/queue")).get(0);

        assertTrue(entry.containsKey("authorHandle"));
        assertFalse(entry.containsKey("authorUserId"), "the raw author id must not be serialized");
        assertFalse(entry.containsKey("sourceEntityId"), "the manuscript UUID must not be serialized");
    }

    @Test
    void queue_asAuthor_excludesOwnRequests() {
        assertTrue(list(get(AS_AUTHOR, "/review/queue")).isEmpty());
    }

    @Test
    void queue_withoutAProfile_is409() {
        Response r = get(AS_NO_PROFILE, "/review/queue");

        assertEquals(409, r.getStatus());
        assertEquals("profile_required", object(r).get("error"));
    }

    @Test
    void queue_genreFilter_passesThrough() {
        // Query params must be attached with .queryParam — folding them into the
        // path string makes UriBuilder percent-encode the '?', which matches no route.
        Response match = AS_REVIEWER.target("/review/queue").queryParam("genre", "fantasy").request(MediaType.APPLICATION_JSON).get();
        assertEquals(Status.OK.getStatusCode(), match.getStatus());
        assertEquals(1, list(match).size());

        Response miss = AS_REVIEWER.target("/review/queue").queryParam("genre", "horror").request(MediaType.APPLICATION_JSON).get();
        assertTrue(list(miss).isEmpty());
    }

    // =========================================================================
    // Package view
    // =========================================================================

    @Test
    void package_asReviewer_hasMetadataButNotTheText() {
        Response r = get(AS_REVIEWER, "/review/packages/" + request.getId());
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        Map<String, Object> pkg = object(r);
        assertEquals("A package", pkg.get("title"));
        assertEquals("Author_One", pkg.get("authorHandle"));
        assertEquals("Book One", pkg.get("bookTitle"));
        assertFalse(pkg.containsKey("contentHtml"), "the package view must not carry the frozen text");
    }

    @Test
    void package_unknown_is404() {
        Response r = get(AS_REVIEWER, "/review/packages/" + UUID.randomUUID());
        assertEquals(404, r.getStatus());
        assertEquals("not_found", object(r).get("error"));
    }

    @Test
    void package_paused_is404ToAStranger() throws SQLException {
        PUBLISH.pause(TEST_USER_ID, request.getId());

        Response r = get(AS_REVIEWER, "/review/packages/" + request.getId());
        assertEquals(404, r.getStatus());
    }

    // =========================================================================
    // Snapshot reader
    // =========================================================================

    @Test
    void snapshot_asReviewer_hasContentButNeverTheSourceId() {
        Response r = get(AS_REVIEWER, "/review/packages/" + request.getId() + "/snapshot");
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        Map<String, Object> snap = object(r);
        assertEquals("<p>One two three four.</p>", snap.get("contentHtml"));
        assertFalse(snap.containsKey("sourceEntityId"), "the manuscript UUID must stay off the wire");
    }

    @Test
    void snapshot_withoutAProfile_is409() {
        Response r = get(AS_NO_PROFILE, "/review/packages/" + request.getId() + "/snapshot");
        assertEquals(409, r.getStatus());
    }
}