package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AiReviewDao;
import com.richardsand.novelkms.model.AiReview;
import com.richardsand.novelkms.service.AiReviewService;
import com.richardsand.novelkms.service.TrashService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
 * AI chapter review endpoints.
 *
 * <p>
 * Authorization: {@code POST /ai/reviews/chapters/{chapterId}} and
 * {@code GET /chapters/{chapterId}/reviews} are covered by the tenant filter
 * (the {@code chapters/{id}} segment triggers an ownership check). The
 * {@code reviews/{id}} paths are NOT covered by the filter, so review ownership
 * is enforced here via {@link AiReviewDao#findByIdForUser}.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiReviewResource {

    private static final Set<String> VALID_STATUSES = Set.of(
            "OPEN",
            "ACCEPTED",
            "REJECTED",
            "FUTURE",
            "DELETED",
            "PROMOTED");

    private final AiReviewService service;
    private final AiReviewDao     reviewDao;
    private final TrashService    trashService;

    @Context
    ContainerRequestContext request;

    @Inject
    public AiReviewResource(AiReviewService service, AiReviewDao reviewDao, TrashService trashService) {
        this.service = service;
        this.reviewDao = reviewDao;
        this.trashService = trashService;
    }

    public static class RunRequest {
        @JsonProperty
        public UUID   credentialId;
        @JsonProperty
        public String model;
    }

    public static class StatusRequest {
        @JsonProperty
        public String status;
    }

    public static class PromoteRequest {
        @JsonProperty
        public String codexCategory;
        @JsonProperty
        public String codexTitle;
    }

    @POST
    @Path("/ai/reviews/chapters/{chapterId}")
    public Response runChapterReview(@PathParam("chapterId") UUID chapterId, RunRequest body) {
        UUID   userId       = CurrentUser.id(request);
        UUID   credentialId = body == null ? null : body.credentialId;
        String model        = body == null ? null : body.model;
        try {
            AiReview review = service.runChapterReview(userId, chapterId, credentialId, model);
            return Response.ok(review).build();
        } catch (ReviewException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", e.getMessage()))
                    .build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    @GET
    @Path("/ai/reviews/{reviewId}")
    public Response getReview(@PathParam("reviewId") UUID reviewId) {
        return run(() -> reviewDao.findByIdForUser(reviewId, CurrentUser.id(request))
                .map(review -> Response.ok(review).build())
                .orElseGet(() -> notFound()));
    }

    /**
     * Moves a whole review artifact to the per-user trash. Ownership is enforced
     * by {@link TrashService} (the trash context query is scoped to the caller's
     * user id), so this path does not rely on the tenant filter.
     */
    @DELETE
    @Path("/ai/reviews/{reviewId}")
    public Response deleteReview(@PathParam("reviewId") UUID reviewId) {
        return run(() -> trashService.trashReview(CurrentUser.id(request), reviewId).isPresent()
                ? Response.noContent().build()
                : notFound());
    }

    @POST
    @Path("/ai/reviews/{reviewId}/recommendations/{recId}/promote")
    public Response promoteRecommendation(@PathParam("reviewId") UUID reviewId,
            @PathParam("recId") UUID recId,
            PromoteRequest body) {
        UUID userId = CurrentUser.id(request);
        try {
            AiReview review = service.promoteRecommendation(
                    userId,
                    reviewId,
                    recId,
                    body == null ? null : body.codexCategory,
                    body == null ? null : body.codexTitle);
            return Response.ok(review).build();
        } catch (ReviewException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", e.getMessage()))
                    .build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    @GET
    @Path("/chapters/{chapterId}/reviews")
    public Response listForChapter(@PathParam("chapterId") UUID chapterId) {
        return run(() -> Response.ok(reviewDao.findByChapter(chapterId)).build());
    }

    @PUT
    @Path("/ai/reviews/{reviewId}/recommendations/{recId}")
    public Response setRecommendationStatus(@PathParam("reviewId") UUID reviewId,
            @PathParam("recId") UUID recId,
            StatusRequest body) {
        if (body == null || body.status == null) {
            return error(400, "bad_request", "A status is required.");
        }
        String status = body.status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(status)) {
            return error(400, "invalid_status",
                    "status must be OPEN, ACCEPTED, REJECTED, FUTURE, DELETED, or PROMOTED.");
        }
        return run(() -> {
            UUID userId = CurrentUser.id(request);
            // Ownership: the review (and therefore its recommendations) must belong to the user.
            if (reviewDao.findByIdForUser(reviewId, userId).isEmpty()) {
                return notFound();
            }
            if (!reviewDao.updateRecommendationStatus(recId, reviewId, status)) {
                return notFound();
            }
            return reviewDao.findByIdForUser(reviewId, userId)
                    .map(review -> Response.ok(review).build())
                    .orElseGet(() -> notFound());
        });
    }

    private static Response notFound() {
        return Response.status(404).entity(Map.of("error", "not_found")).build();
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("error", code, "message", message)).build();
    }

    private Response run(SqlCall call) {
        try {
            return call.call();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    private interface SqlCall {
        Response call() throws SQLException;
    }
}
