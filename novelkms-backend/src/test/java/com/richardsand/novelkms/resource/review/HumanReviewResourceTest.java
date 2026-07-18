package com.richardsand.novelkms.resource.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.HumanReviewService;
import com.richardsand.novelkms.service.ReviewPublishService;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * The HTTP contract for {@link HumanReviewResource}. The rules live in
 * {@code HumanReviewServiceTest}; what is pinned here is the wiring and, above all,
 * <b>what does and does not appear on the wire</b>.
 *
 * <p>Reviews are the first cross-user <em>write</em> in NovelKMS, and both sides of
 * a review see each other only through the public-identity keyhole: a writing row
 * exposes the author's handle and never their user id; a received row exposes the
 * reviewer's handle and the feedback body, never the reviewer's user id; and the
 * reviewer's own review object keeps its internal ids ({@code reviewerUserId},
 * {@code snapshotId}) and the author's private {@code authorReadAt} off the wire. A
 * field slipping back into the JSON is a leak a service test cannot catch.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class HumanReviewResourceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final HumanReviewDao    REVIEWS   = new HumanReviewDao(ds);
    private static final UserBlockDao      BLOCKS    = new UserBlockDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private static final HumanReviewService SERVICE = new HumanReviewService(
            REQUESTS, SNAPSHOTS, PROFILES, REVIEWS, BLOCKS);

    static final ResourceExtension AS_AUTHOR = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(TEST_USER_ID))
            .addResource(new HumanReviewResource(SERVICE))
            .setMapper(createMapper())
            .build();

    static final ResourceExtension AS_REVIEWER = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(OTHER_USER_ID))
            .addResource(new HumanReviewResource(SERVICE))
            .setMapper(createMapper())
            .build();

    // THIRD_USER never claims a handle — the un-profiled caller.
    static final ResourceExtension AS_NO_PROFILE = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter(THIRD_USER_ID))
            .addResource(new HumanReviewResource(SERVICE))
            .setMapper(createMapper())
            .build();

    private ReviewRequest request;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        PROFILES.create(TEST_USER_ID, ReviewProfile.builder().handle("Author_One").displayName("Ann Author").build());
        PROFILES.create(OTHER_USER_ID, ReviewProfile.builder().handle("Reviewer_Two").displayName("Rev Two").build());

        Project project = createTestProject("The Long Road", null);
        Book book = bookDao.create(project.getId(), "Book One", null, null, null);
        Chapter chapter = chapterDao.create(book.getId(), null, "The Crossing", null, null);
        UUID sceneId = sceneDao.create(chapter.getId(), "Scene", null).getId();
        sceneDao.saveContent(sceneId, "<p>One two three four.</p>", 0);

        request = PUBLISH.publishChapter(TEST_USER_ID, chapter.getId(),
                ReviewRequest.builder().title("A package").genre("fantasy").build());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Map<String, Object> object(Response r) {
        return r.readEntity(new GenericType<Map<String, Object>>() { });
    }

    private static List<Map<String, Object>> list(Response r) {
        return r.readEntity(new GenericType<List<Map<String, Object>>>() { });
    }

    private static Response get(ResourceExtension as, String path) {
        return as.target(path).request(MediaType.APPLICATION_JSON).get();
    }

    private static Response put(ResourceExtension as, String path, Object body) {
        return as.target(path).request(MediaType.APPLICATION_JSON).put(Entity.json(body));
    }

    private static Response post(ResourceExtension as, String path, Object body) {
        return as.target(path).request(MediaType.APPLICATION_JSON)
                .post(body == null ? Entity.json("{}") : Entity.json(body));
    }

    private String reviewPath() {
        return "/review/packages/" + request.getId() + "/review";
    }

    // =========================================================================
    // Draft + my-review
    // =========================================================================

    @Test
    void myReview_whenNone_is204() {
        Response r = get(AS_REVIEWER, reviewPath());
        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void saveDraft_thenMyReview_returnsIt_withoutInternalIds() {
        Response saved = put(AS_REVIEWER, reviewPath(), Map.of("contentHtml", "<p>My draft.</p>", "aiAssisted", false));
        assertEquals(Status.OK.getStatusCode(), saved.getStatus());

        Response r = get(AS_REVIEWER, reviewPath());
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Map<String, Object> body = object(r);

        assertEquals("DRAFT", body.get("status"));
        assertEquals("<p>My draft.</p>", body.get("contentHtml"));
        // Internal linkage and the author's private read-state must never surface.
        assertNull(body.get("reviewerUserId"), "reviewer user id must not be on the wire");
        assertNull(body.get("snapshotId"), "snapshot id must not be on the wire");
        assertNull(body.get("authorReadAt"), "author read-state must not be on the wire");
    }

    @Test
    void author_savingOwnPackage_is400_ownRequest() {
        Response r = put(AS_AUTHOR, reviewPath(), Map.of("contentHtml", "<p>self</p>", "aiAssisted", false));
        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("own_request", object(r).get("error"));
    }

    @Test
    void noProfile_is409_profileRequired() {
        Response r = get(AS_NO_PROFILE, reviewPath());
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        assertEquals("profile_required", object(r).get("error"));
    }

    // =========================================================================
    // Submit + empty guard
    // =========================================================================

    @Test
    void submit_empty_is400() {
        Response r = post(AS_REVIEWER, reviewPath() + "/submit", Map.of("contentHtml", "   ", "aiAssisted", false));
        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertEquals("empty_review", object(r).get("error"));
    }

    @Test
    void submit_thenWithdraw() {
        Response s = post(AS_REVIEWER, reviewPath() + "/submit",
                Map.of("contentHtml", "<p>Solid opening, clear stakes.</p>", "aiAssisted", true));
        assertEquals(Status.OK.getStatusCode(), s.getStatus());
        assertEquals("SUBMITTED", object(s).get("status"));

        Response w = post(AS_REVIEWER, reviewPath() + "/withdraw", null);
        assertEquals(Status.OK.getStatusCode(), w.getStatus());
        assertEquals("WITHDRAWN", object(w).get("status"));
    }

    // =========================================================================
    // Writing list
    // =========================================================================

    @Test
    void writing_carriesAuthorHandle_notUserId() {
        put(AS_REVIEWER, reviewPath(), Map.of("contentHtml", "<p>Draft.</p>", "aiAssisted", false));

        Response r = get(AS_REVIEWER, "/review/reviews/writing");
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Map<String, Object>> rows = list(r);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Author_One", row.get("authorHandle"));
        assertEquals("DRAFT", row.get("status"));
        assertNull(row.get("authorUserId"), "queue/writing rows never carry the author user id");
        assertNull(row.get("sourceEntityId"), "manuscript ids never cross the review wire");
    }

    // =========================================================================
    // Received list + badge + read
    // =========================================================================

    @Test
    void received_showsSubmitted_withReviewerHandle_notUserId() {
        post(AS_REVIEWER, reviewPath() + "/submit", Map.of("contentHtml", "<p>Great pacing throughout.</p>", "aiAssisted", false));

        Response r = get(AS_AUTHOR, "/review/reviews/received");
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Map<String, Object>> rows = list(r);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Reviewer_Two", row.get("reviewerHandle"));
        assertEquals("<p>Great pacing throughout.</p>", row.get("contentHtml"));
        assertEquals(Boolean.FALSE, row.get("read"));
        assertNull(row.get("reviewerUserId"), "received rows never carry the reviewer user id");
    }

    @Test
    void unreadBadge_andMarkRead() {
        post(AS_REVIEWER, reviewPath() + "/submit", Map.of("contentHtml", "<p>Feedback here.</p>", "aiAssisted", false));

        Response before = get(AS_AUTHOR, "/review/reviews/received/unread");
        assertEquals(1, ((Number) object(before).get("count")).intValue());

        UUID reviewId = UUID.fromString((String) list(get(AS_AUTHOR, "/review/reviews/received")).get(0).get("reviewId"));

        Response read = post(AS_AUTHOR, "/review/reviews/received/" + reviewId + "/read", null);
        assertEquals(Status.NO_CONTENT.getStatusCode(), read.getStatus());

        Response after = get(AS_AUTHOR, "/review/reviews/received/unread");
        assertEquals(0, ((Number) object(after).get("count")).intValue());
    }

    @Test
    void markRead_ofAnotherAuthorsReview_is404() {
        post(AS_REVIEWER, reviewPath() + "/submit", Map.of("contentHtml", "<p>Feedback.</p>", "aiAssisted", false));
        UUID reviewId = UUID.fromString((String) list(get(AS_AUTHOR, "/review/reviews/received")).get(0).get("reviewId"));

        // The reviewer does not own the request behind this review.
        Response r = post(AS_REVIEWER, "/review/reviews/received/" + reviewId + "/read", null);
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void reviewer_receivedIsEmpty_authorWritingIsEmpty() {
        post(AS_REVIEWER, reviewPath() + "/submit", Map.of("contentHtml", "<p>Feedback.</p>", "aiAssisted", false));

        // The reviewer authored no requests, so has received nothing.
        assertTrue(list(get(AS_REVIEWER, "/review/reviews/received")).isEmpty());
        // The author wrote no reviews, so is writing nothing.
        assertTrue(list(get(AS_AUTHOR, "/review/reviews/writing")).isEmpty());
        assertFalse(list(get(AS_AUTHOR, "/review/reviews/received")).isEmpty());
    }
}
