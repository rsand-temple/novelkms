package com.richardsand.novelkms.resource.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewSnapshot;
import com.richardsand.novelkms.service.ReviewPublishService;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * The HTTP contract for {@link ReviewRequestResource}. The logic itself is pinned
 * down in {@code ReviewPublishServiceTest}; what is tested here is the wiring —
 * URL shapes, status codes, and the author scoping.
 *
 * <p><b>One thing these tests cannot cover.</b> Chapter ownership on
 * {@code POST /chapters/{chapterId}/review-requests} is enforced by
 * {@code TenantAuthorizationFilter}, which {@code ResourceExtension} does not
 * register. So "user A cannot publish user B's chapter" is guaranteed by the URL
 * shape plus that filter, and is verified by {@code TenantIsolationDaoTest} and the
 * filter's own rules — not here. This is exactly why the endpoint hangs off the
 * manuscript path rather than taking a chapter id in the body: a body-carried id
 * would have no such guard at all.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ReviewRequestResourceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);

    private static final ReviewPublishService SERVICE = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    static final ResourceExtension AS_AUTHOR = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(TEST_USER_ID))
            .addResource(new ReviewRequestResource(SERVICE))
            .addResource(new ReviewPublishResource(SERVICE))
            .setMapper(createMapper())
            .build();

    static final ResourceExtension AS_STRANGER = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(OTHER_USER_ID))
            .addResource(new ReviewRequestResource(SERVICE))
            .addResource(new ReviewPublishResource(SERVICE))
            .setMapper(createMapper())
            .build();

    private Chapter chapter;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();

        PROFILES.create(TEST_USER_ID, ReviewProfile.builder().handle("Author_One").build());

        Project project = createTestProject("The Long Road", null);
        Book    book    = bookDao.create(project.getId(), "Book One", null, null, null);
        chapter = chapterDao.create(book.getId(), null, "The Crossing", null, null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void scene(String html) throws SQLException {
        UUID id = sceneDao.create(chapter.getId(), "Scene", null).getId();
        sceneDao.saveContent(id, html, 0);
    }

    private static Map<String, Object> form(String title) {
        Map<String, Object> f = new HashMap<>();
        f.put("title", title);
        return f;
    }

    private Response publish(ResourceExtension as, Map<String, Object> f) {
        return as.target("/chapters/" + chapter.getId() + "/review-requests")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(f));
    }

    private static Response post(ResourceExtension as, String path) {
        return as.target(path).request(MediaType.APPLICATION_JSON).post(Entity.json(Map.of()));
    }

    private static String errorCode(Response r) {
        return String.valueOf(r.readEntity(new GenericType<Map<String, Object>>() {}).get("error"));
    }

    // =========================================================================
    // Publish
    // =========================================================================

    @Test
    void publish_returnsTheOpenedRequest() throws SQLException {
        scene("<p>One two three.</p>");

        Response r = publish(AS_AUTHOR, form("Crossing — draft 3"));

        assertEquals(200, r.getStatus());
        ReviewRequest created = r.readEntity(ReviewRequest.class);
        assertEquals("Crossing — draft 3", created.getTitle());
        assertEquals(ReviewRequestDao.STATUS_OPEN, created.getStatus());
        assertEquals(chapter.getId(), created.getSourceEntityId());
        assertNotNull(created.getPublishedAt());

        assertTrue(SNAPSHOTS.findByRequestId(created.getId()).isPresent());
    }

    @Test
    void publish_emptyChapter_is400() {
        Response r = publish(AS_AUTHOR, form("Nothing here"));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("empty_chapter", errorCode(r));
    }

    @Test
    void publish_withoutAHandle_is409() throws SQLException {
        scene("<p>Text.</p>");

        // OTHER_USER_ID has never claimed a handle. (Chapter ownership is the tenant
        // filter's job, and it is not in this harness — see the class comment.)
        Response r = publish(AS_STRANGER, form("Uninvited"));

        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("profile_required", errorCode(r));
    }

    // =========================================================================
    // Read
    // =========================================================================

    @Test
    void mine_listsTheAuthorsOwnRequests() throws SQLException {
        scene("<p>One two three.</p>");
        publish(AS_AUTHOR, form("Package A"));

        Response r = AS_AUTHOR.target("/review/requests").request().get();

        assertEquals(200, r.getStatus());
        List<Map<String, Object>> mine = r.readEntity(new GenericType<List<Map<String, Object>>>() {});
        assertEquals(1, mine.size());
        assertEquals("Package A", mine.get(0).get("title"));
        assertEquals("The Crossing", mine.get(0).get("sourceTitle"));
        assertEquals("CURRENT", mine.get(0).get("sourceState"));
        assertEquals(3, mine.get(0).get("wordCount"));
    }

    @Test
    void mine_isEmptyForSomeoneElse() throws SQLException {
        scene("<p>Text.</p>");
        publish(AS_AUTHOR, form("Package A"));

        Response r = AS_STRANGER.target("/review/requests").request().get();

        assertEquals(200, r.getStatus());
        assertTrue(r.readEntity(new GenericType<List<Map<String, Object>>>() {}).isEmpty());
    }

    @Test
    void one_anotherAuthorsRequest_is404NotForbidden() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("Private")).readEntity(ReviewRequest.class).getId();

        Response r = AS_STRANGER.target("/review/requests/" + id).request().get();

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void snapshot_returnsTheFrozenTextToItsAuthor() throws SQLException {
        scene("<p>Frozen.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        Response r = AS_AUTHOR.target("/review/requests/" + id + "/snapshot").request().get();

        assertEquals(200, r.getStatus());
        ReviewSnapshot s = r.readEntity(ReviewSnapshot.class);
        assertEquals("<p>Frozen.</p>", s.getContentHtml());
        assertEquals("The Crossing", s.getSourceTitle());
    }

    /** The reviewer has no use for the author's internal scene/chapter UUIDs. */
    @Test
    void snapshot_doesNotSerializeTheSourceEntityId() throws SQLException {
        scene("<p>Frozen.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        Map<String, Object> raw = AS_AUTHOR.target("/review/requests/" + id + "/snapshot")
                .request().get()
                .readEntity(new GenericType<Map<String, Object>>() {});

        assertFalse(raw.containsKey("sourceEntityId"));
        assertFalse(raw.containsKey("sourceUpdatedAt"));
    }

    @Test
    void snapshot_anotherAuthorsRequest_is404() throws SQLException {
        scene("<p>Frozen.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        Response r = AS_STRANGER.target("/review/requests/" + id + "/snapshot").request().get();

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    // =========================================================================
    // Edit and lifecycle
    // =========================================================================

    @Test
    void update_changesMetadata() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("Before")).readEntity(ReviewRequest.class).getId();

        Map<String, Object> f = form("After");
        f.put("description", "Now described.");
        f.put("feedbackTypes", List.of("developmental"));
        f.put("visibility", "INVITE");

        Response r = AS_AUTHOR.target("/review/requests/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(f));

        assertEquals(200, r.getStatus());
        ReviewRequest updated = r.readEntity(ReviewRequest.class);
        assertEquals("After", updated.getTitle());
        assertEquals("Now described.", updated.getDescription());
        assertEquals(List.of("developmental"), updated.getFeedbackTypes());
        assertEquals("INVITE", updated.getVisibility());
        assertEquals(ReviewRequestDao.STATUS_OPEN, updated.getStatus());
    }

    @Test
    void update_anotherAuthorsRequest_is404() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("Mine")).readEntity(ReviewRequest.class).getId();

        Response r = AS_STRANGER.target("/review/requests/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(form("Hijacked")));

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
        assertEquals("Mine", REQUESTS.findById(id).orElseThrow().getTitle());
    }

    @Test
    void lifecycle_pauseResumeCloseAreReachableOverHttp() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();
        String base = "/review/requests/" + id;

        assertEquals(ReviewRequestDao.STATUS_PAUSED,
                post(AS_AUTHOR, base + "/pause").readEntity(ReviewRequest.class).getStatus());
        assertEquals(ReviewRequestDao.STATUS_OPEN,
                post(AS_AUTHOR, base + "/resume").readEntity(ReviewRequest.class).getStatus());

        ReviewRequest closed = post(AS_AUTHOR, base + "/close").readEntity(ReviewRequest.class);
        assertEquals(ReviewRequestDao.STATUS_CLOSED, closed.getStatus());
        assertNotNull(closed.getClosedAt());
    }

    @Test
    void withdraw_isReachableOverHttp() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        Response r = post(AS_AUTHOR, "/review/requests/" + id + "/withdraw");

        assertEquals(200, r.getStatus());
        assertEquals(ReviewRequestDao.STATUS_WITHDRAWN, r.readEntity(ReviewRequest.class).getStatus());
    }

    @Test
    void invalidTransition_is409() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        // Already OPEN — there is nothing to resume.
        Response r = post(AS_AUTHOR, "/review/requests/" + id + "/resume");

        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("invalid_transition", errorCode(r));
    }

    @Test
    void lifecycle_cannotBeMovedByAnotherAuthor() throws SQLException {
        scene("<p>Text.</p>");
        UUID id = publish(AS_AUTHOR, form("P")).readEntity(ReviewRequest.class).getId();

        Response r = post(AS_STRANGER, "/review/requests/" + id + "/close");

        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
        assertEquals(ReviewRequestDao.STATUS_OPEN, REQUESTS.findById(id).orElseThrow().getStatus());
    }
}
