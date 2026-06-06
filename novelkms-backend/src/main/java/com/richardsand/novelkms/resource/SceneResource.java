package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Scene;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SceneResource {
    private static final Logger logger = LoggerFactory.getLogger(SceneResource.class);

    private final SceneDao sceneDao;

    @Inject
    public SceneResource(SceneDao sceneDao) {
        this.sceneDao = sceneDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String notes;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String notes;
    }

    public static class SaveContentRequest {
        @JsonProperty
        public String content;
        @JsonProperty
        public int    wordCount;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    @Path("/chapters/{chapterId}/scenes")
    public Response listScenes(@PathParam("chapterId") UUID chapterId) {
        try {
            List<Scene> scenes = sceneDao.findByChapterId(chapterId);
            return Response.ok(scenes).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @GET
    @Path("/scenes/{id}")
    public Response getScene(@PathParam("id") UUID id) {
        try {
            return sceneDao.findById(id)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @POST
    @Path("/chapters/{chapterId}/scenes")
    public Response createScene(@PathParam("chapterId") UUID chapterId, CreateRequest req) {
        if (req == null || req.title == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Scene scene = sceneDao.create(chapterId, req.title, req.notes);
            return Response.status(Response.Status.CREATED).entity(scene).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @PUT
    @Path("/scenes/{id}")
    public Response updateScene(@PathParam("id") UUID id, UpdateRequest req) {
        if (req == null || req.title == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("title is required").build();
        }
        try {
            return sceneDao.update(id, req.title, req.notes)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    /**
     * Dedicated endpoint for saving scene content (the TipTap document).
     * Separated from metadata updates to support auto-save without clobbering
     * structural edits happening concurrently.
     */
    @PUT
    @Path("/scenes/{id}/content")
    public Response saveContent(@PathParam("id") UUID id, SaveContentRequest req) {
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return sceneDao.saveContent(id, req.content, req.wordCount)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @DELETE
    @Path("/scenes/{id}")
    public Response deleteScene(@PathParam("id") UUID id) {
        try {
            return sceneDao.delete(id)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
