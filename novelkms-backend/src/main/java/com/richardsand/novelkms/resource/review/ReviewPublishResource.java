package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.ReviewException;
import com.richardsand.novelkms.service.ReviewPublishService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Publishes a chapter for human review.
 *
 * <p>This is a separate resource from {@link ReviewRequestResource} because it
 * <em>must</em> live at {@code @Path("/")} so the URL contains
 * {@code /chapters/{chapterId}/...} — the {@code TenantAuthorizationFilter}
 * authorizes any UUID following a {@code chapters} segment, which gives this
 * endpoint chapter-ownership checking for free. A {@code chapterId} carried in
 * the request body would be <em>unauthorized</em>.
 *
 * <p>The other request-management endpoints live on
 * {@code ReviewRequestResource @Path("/review")} where they share the class
 * prefix with the rest of the review network and avoid the Jersey path-specificity
 * clash that caused the {@code @Path("/")} resource's {@code /review/requests}
 * method to be shadowed by any {@code @Path("/review")} resource.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewPublishResource {

    private final ReviewPublishService service;

    @Inject
    public ReviewPublishResource(ReviewPublishService service) {
        this.service = service;
    }

    @POST
    @Path("/chapters/{chapterId}/review-requests")
    public Response publish(@PathParam("chapterId") UUID chapterId,
            ReviewRequestResource.RequestForm form,
            @Context ContainerRequestContext request) {

        return run(() -> Response.ok(
                service.publishChapter(CurrentUser.id(request), chapterId,
                        ReviewRequestResource.toModel(form))).build());
    }

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
