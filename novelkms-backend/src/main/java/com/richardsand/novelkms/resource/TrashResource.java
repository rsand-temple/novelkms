package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.TrashItem;
import com.richardsand.novelkms.service.TrashException;
import com.richardsand.novelkms.service.TrashService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The per-user trash can.
 *
 * <p>Authorization: these paths carry a {@code trash_batch} id rather than a
 * tenant entity UUID, so the tenant filter does not check them. Ownership is
 * enforced here by resolving the caller via {@link CurrentUser} and scoping
 * every {@link TrashService} call to that user.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrashResource {

    private final TrashService trash;

    @Context
    ContainerRequestContext request;

    @Inject
    public TrashResource(TrashService trash) {
        this.trash = trash;
    }

    @GET
    @Path("/trash")
    public Response list() {
        try {
            return Response.ok(trash.list(CurrentUser.id(request))).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @POST
    @Path("/trash/{batchId}/restore")
    public Response restore(@PathParam("batchId") UUID batchId) {
        try {
            TrashItem restored = trash.restore(CurrentUser.id(request), batchId);
            return Response.ok(restored).build();
        } catch (TrashException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", e.getMessage()))
                    .build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/trash/{batchId}")
    public Response purge(@PathParam("batchId") UUID batchId) {
        try {
            trash.purge(CurrentUser.id(request), batchId);
            return Response.noContent().build();
        } catch (TrashException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", e.getMessage()))
                    .build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @POST
    @Path("/trash/empty")
    public Response empty() {
        try {
            int purged = trash.empty(CurrentUser.id(request));
            return Response.ok(Map.of("purged", purged)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    private static Response serverError() {
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
