package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.service.HumanReviewService;
import com.richardsand.novelkms.service.ReviewException;

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
 * Writing and receiving reviews (slice 1D) — the reviewer's write side of the
 * human-review network, and the author's view of what came back.
 *
 * <ul>
 *   <li>{@code GET  /review/packages/{requestId}/review}         — my review of this package</li>
 *   <li>{@code PUT  /review/packages/{requestId}/review}         — save my draft</li>
 *   <li>{@code POST /review/packages/{requestId}/review/submit}  — submit it</li>
 *   <li>{@code POST /review/packages/{requestId}/review/withdraw}— retract it</li>
 *   <li>{@code GET  /review/reviews/writing}                     — Reviews I'm Writing</li>
 *   <li>{@code GET  /review/reviews/received}                    — Reviews Received</li>
 *   <li>{@code GET  /review/reviews/received/unread}             — unread badge count</li>
 *   <li>{@code POST /review/reviews/received/{reviewId}/read}    — mark feedback read</li>
 * </ul>
 *
 * <p>A review is modeled as a sub-resource of the package it is against, so the
 * write endpoints hang off {@code /review/packages/{requestId}/review} — a
 * different, more specific sub-path than {@code ReviewQueueResource}'s
 * {@code /review/packages/{requestId}} and {@code .../snapshot}, which the two
 * resources own side by side without collision.
 *
 * <p>These paths fall through {@code TenantAuthorizationFilter}'s
 * {@code default -> true}, so authorization is entirely in {@link HumanReviewService},
 * which returns <b>404, not 403</b>, for anything cross-user the caller may not
 * touch. Everything sits behind {@code SubscriptionAuthorizationFilter} and a
 * review-profile gate. The request context is a method parameter, never a field:
 * Jersey does not proxy {@code ContainerRequestContext} into a singleton resource's
 * fields, which is how {@code ResourceExtension} registers one in tests.
 */
@Path("/review")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HumanReviewResource {

    private final HumanReviewService service;

    @Inject
    public HumanReviewResource(HumanReviewService service) {
        this.service = service;
    }

    /**
     * The reviewer's write payload. Carries only the body and the AI-assist
     * self-disclosure. There is no {@code visibility} field: reviews are PRIVATE in
     * Phase 1, exactly as request publish declined to expose INVITE visibility for a
     * surface nobody could yet reach. Identity comes from the session and the target
     * from the URL, so neither can be forged because neither exists here.
     */
    public static class ReviewForm {
        @JsonProperty
        public String contentHtml;

        @JsonProperty
        public boolean aiAssisted;
    }

    // =========================================================================
    // Reviewer — my review of a package
    // =========================================================================

    @GET
    @Path("/packages/{requestId}/review")
    public Response myReview(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> service.myReview(CurrentUser.id(request), requestId)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.noContent().build()));
    }

    @PUT
    @Path("/packages/{requestId}/review")
    public Response save(@PathParam("requestId") UUID requestId,
            ReviewForm form,
            @Context ContainerRequestContext request) {

        ReviewForm f = form == null ? new ReviewForm() : form;
        return run(() -> Response.ok(
                service.saveDraft(CurrentUser.id(request), requestId, f.contentHtml, f.aiAssisted)).build());
    }

    @POST
    @Path("/packages/{requestId}/review/submit")
    public Response submit(@PathParam("requestId") UUID requestId,
            ReviewForm form,
            @Context ContainerRequestContext request) {

        ReviewForm f = form == null ? new ReviewForm() : form;
        return run(() -> Response.ok(
                service.submit(CurrentUser.id(request), requestId, f.contentHtml, f.aiAssisted)).build());
    }

    @POST
    @Path("/packages/{requestId}/review/withdraw")
    public Response withdraw(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> {
            HumanReview review = service.withdraw(CurrentUser.id(request), requestId);
            return Response.ok(review).build();
        });
    }

    // =========================================================================
    // Reviewer — Reviews I'm Writing
    // =========================================================================

    @GET
    @Path("/reviews/writing")
    public Response writing(@Context ContainerRequestContext request) {
        return run(() -> Response.ok(service.writing(CurrentUser.id(request))).build());
    }

    // =========================================================================
    // Author — Reviews Received
    // =========================================================================

    @GET
    @Path("/reviews/received")
    public Response received(@Context ContainerRequestContext request) {
        return run(() -> Response.ok(service.received(CurrentUser.id(request))).build());
    }

    @GET
    @Path("/reviews/received/unread")
    public Response unread(@Context ContainerRequestContext request) {
        return run(() -> Response.ok(
                Map.of("count", service.unreadReceivedCount(CurrentUser.id(request)))).build());
    }

    @POST
    @Path("/reviews/received/{reviewId}/read")
    public Response markRead(@PathParam("reviewId") UUID reviewId,
            @Context ContainerRequestContext request) {

        return run(() -> {
            service.markReceivedRead(CurrentUser.id(request), reviewId);
            return Response.noContent().build();
        });
    }

    // =========================================================================
    // Plumbing — the service owns the status code, the resource forwards it.
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
