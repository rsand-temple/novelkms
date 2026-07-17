package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.service.ReviewAccessService;
import com.richardsand.novelkms.service.ReviewException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The reviewer's side of the human-review network (slice 1C): browse the queue,
 * view a package, read its frozen snapshot.
 *
 * <ul>
 *   <li>{@code GET /review/queue}                       — the public queue</li>
 *   <li>{@code GET /review/packages/{requestId}}        — one package's metadata</li>
 *   <li>{@code GET /review/packages/{requestId}/snapshot} — the frozen text</li>
 * </ul>
 *
 * <p>Two different URL prefixes, two different authorization models, on purpose.
 * {@code /review/requests/...} ({@link ReviewRequestResource}) is the author reading
 * their own; {@code /review/queue} and {@code /review/packages/...} here are the
 * cross-user reader path, authorized in {@link ReviewAccessService} — which returns
 * <b>404, not 403</b>, for anything the viewer may not see. Naming the read path
 * {@code packages} rather than {@code queue} keeps the URL honest once a CLOSED
 * package stays readable to its participants (§30.2 Q5) while having dropped out of
 * the queue.
 *
 * <p>Both surfaces sit behind {@code SubscriptionAuthorizationFilter} (participating
 * requires an active subscription) and, in this service, behind a review-profile
 * gate. The request context is a method parameter, never a field: Jersey does not
 * proxy {@code ContainerRequestContext} into the fields of a singleton resource,
 * which is how {@code ResourceExtension} registers one.
 */
@Path("/review")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewQueueResource {

    private final ReviewAccessService service;

    @Inject
    public ReviewQueueResource(ReviewAccessService service) {
        this.service = service;
    }

    @GET
    @Path("/queue")
    public Response queue(@QueryParam("genre") String genre,
            @QueryParam("minWords") Integer minWords,
            @QueryParam("maxWords") Integer maxWords,
            @QueryParam("sort") String sort,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.queue(
                CurrentUser.id(request), genre, minWords, maxWords, sort, limit, offset)).build());
    }

    @GET
    @Path("/packages/{requestId}")
    public Response pkg(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.viewPackage(CurrentUser.id(request), requestId)).build());
    }

    @GET
    @Path("/packages/{requestId}/snapshot")
    public Response snapshot(@PathParam("requestId") UUID requestId,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(service.snapshot(CurrentUser.id(request), requestId)).build());
    }

    // =========================================================================
    // Plumbing — identical contract to ReviewRequestResource: the service owns the
    // status code, the resource only forwards it.
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
