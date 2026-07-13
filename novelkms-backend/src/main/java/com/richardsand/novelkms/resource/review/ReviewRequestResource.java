package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.ReviewException;
import com.richardsand.novelkms.service.ReviewPublishService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Publishing a chapter for human review, and the lifecycle of the resulting
 * package. Author-facing only — the reviewer's side of the network arrives in
 * slice 1C.
 *
 * <ul>
 *   <li>{@code POST /chapters/{chapterId}/review-requests} — freeze and publish</li>
 *   <li>{@code GET  /review/requests}                      — the author's own requests</li>
 *   <li>{@code GET  /review/requests/{id}}                 — one request</li>
 *   <li>{@code GET  /review/requests/{id}/snapshot}        — the frozen text</li>
 *   <li>{@code PUT  /review/requests/{id}}                 — edit metadata</li>
 *   <li>{@code POST /review/requests/{id}/{pause|resume|close|withdraw}}</li>
 * </ul>
 *
 * <p><b>Why publish hangs off the manuscript path.</b> {@code TenantAuthorizationFilter}
 * authorizes any UUID following a {@code chapters} segment, so
 * {@code POST /chapters/{chapterId}/review-requests} gets chapter-ownership
 * checking for free. A {@code chapterId} carried in the request body would be
 * <em>unauthorized</em> — {@code authorizeSensitiveJsonBody} only inspects
 * {@code /move}, {@code /reorder}, and {@code POST books/{id}/chapters}. The URL
 * shape is load-bearing, not cosmetic.
 *
 * <p>Conversely, {@code /review/...} paths fall through the tenant filter's
 * {@code default -> true}, so ownership there is enforced in the service, which
 * returns <b>404 rather than 403</b> for a request belonging to someone else — the
 * caller learns nothing about packages that are not theirs.
 *
 * <p>The request context is a method parameter, never a field: Jersey does not
 * proxy {@code ContainerRequestContext} into the fields of a singleton resource,
 * which is how {@code ResourceExtension} registers one.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewRequestResource {

    private final ReviewPublishService service;

    @Inject
    public ReviewRequestResource(ReviewPublishService service) {
        this.service = service;
    }

    /**
     * Publish/edit payload. Carries no {@code id}, {@code status},
     * {@code sourceEntityId}, or snapshot: identity comes from the session, the
     * source comes from the URL, and the status moves only through the lifecycle
     * endpoints. None of them can be forged because none of them exist here.
     */
    public static class RequestForm {
        @JsonProperty
        public String title;

        @JsonProperty
        public String description;

        @JsonProperty
        public String authorQuestions;

        @JsonProperty
        public String genre;

        @JsonProperty
        public List<String> feedbackTypes;

        @JsonProperty
        public String contentWarnings;

        @JsonProperty
        public String visibility;

        @JsonProperty
        public Integer maxReviews;

        @JsonProperty
        public Instant closesAt;
    }

    private static ReviewRequest toModel(RequestForm f) {
        if (f == null) {
            throw new ReviewException(400, "missing_body", "A review request payload is required.");
        }
        return ReviewRequest.builder()
                .title(f.title)
                .description(f.description)
                .authorQuestions(f.authorQuestions)
                .genre(f.genre)
                .feedbackTypes(f.feedbackTypes)
                .contentWarnings(f.contentWarnings)
                .visibility(f.visibility)
                .maxReviews(f.maxReviews)
                .closesAt(f.closesAt)
                .build();
    }

    // =========================================================================
    // Publish
    // =========================================================================

    @POST
    @Path("/chapters/{chapterId}/review-requests")
    public Response publish(@PathParam("chapterId") UUID chapterId,
            RequestForm form,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(
                service.publishChapter(CurrentUser.id(request), chapterId, toModel(form))).build());
    }

    // =========================================================================
    // Read
    // =========================================================================

    @GET
    @Path("/review/requests")
    public Response mine(@Context ContainerRequestContext request) {
        return run(() -> Response.ok(service.mine(CurrentUser.id(request))).build());
    }

    @GET
    @Path("/review/requests/{requestId}")
    public Response one(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(
                service.requireOwned(CurrentUser.id(request), requestId)).build());
    }

    @GET
    @Path("/review/requests/{requestId}/snapshot")
    public Response snapshot(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(
                service.snapshotFor(CurrentUser.id(request), requestId)).build());
    }

    // =========================================================================
    // Edit and lifecycle
    // =========================================================================

    @PUT
    @Path("/review/requests/{requestId}")
    public Response update(@PathParam("requestId") UUID requestId,
            RequestForm form,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(
                service.updateMetadata(CurrentUser.id(request), requestId, toModel(form))).build());
    }

    @POST
    @Path("/review/requests/{requestId}/pause")
    public Response pause(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.pause(CurrentUser.id(request), requestId)).build());
    }

    @POST
    @Path("/review/requests/{requestId}/resume")
    public Response resume(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.resume(CurrentUser.id(request), requestId)).build());
    }

    @POST
    @Path("/review/requests/{requestId}/close")
    public Response close(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.close(CurrentUser.id(request), requestId)).build());
    }

    @POST
    @Path("/review/requests/{requestId}/withdraw")
    public Response withdraw(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.withdraw(CurrentUser.id(request), requestId)).build());
    }

    // =========================================================================
    // Plumbing
    // =========================================================================

    private Response run(Call c) {
        try {
            return c.go();
        } catch (ReviewException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", String.valueOf(e.getMessage())))
                    .build();
        } catch (SQLException e) {
            return Response.serverError()
                    .entity(Map.of("error", "server_error", "message", String.valueOf(e.getMessage())))
                    .build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
