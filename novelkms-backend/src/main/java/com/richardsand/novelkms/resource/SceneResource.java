package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Scene;
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

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SceneResource {
    private static final Logger logger = LoggerFactory.getLogger(SceneResource.class);

    private final SceneDao sceneDao;
    private final TrashService trashService;

    @Inject
    public SceneResource(SceneDao sceneDao, TrashService trashService) {
        this.sceneDao = sceneDao;
        this.trashService = trashService;
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
        logger.debug("SceneResource.listScenes invoked: chapterId={}", chapterId);
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
        logger.debug("SceneResource.getScene invoked: id={}", id);
        try {
            return sceneDao.findById(id)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @POST
    @Path("/chapters/{chapterId}/scenes")
    public Response createScene(@PathParam("chapterId") UUID chapterId, CreateRequest req) {
        logger.info("SceneResource.createScene invoked: chapterId={}", chapterId);
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
        logger.info("SceneResource.updateScene invoked: id={}", id);
        if (req == null || req.title == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("title is required").build();
        }
        try {
            return sceneDao.update(id, req.title, req.notes)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    /**
     * Dedicated endpoint for saving scene content (the TipTap document).
     * Separated from metadata updates to support auto-save without clobbering
     * structural edits happening concurrently.
     *
     * wordCount must be supplied by the caller (the frontend):
     * single-scene mode — TipTap CharacterCount.words()
     * multi-scene mode — per-chunk countWords() HTML-strip helper
     * Both use /\S+/ matching, consistent with the status bar display.
     */
    @PUT
    @Path("/scenes/{id}/content")
    public Response saveContent(@PathParam("id") UUID id, SaveContentRequest req) {
        logger.debug("SceneResource.saveContent invoked: id={}, wordCount={}", id, req == null ? null : req.wordCount);
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return sceneDao.saveContent(id, req.content, req.wordCount)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @DELETE
    @Path("/scenes/{id}")
    public Response deleteScene(@PathParam("id") UUID id, @Context ContainerRequestContext request) {
        logger.info("SceneResource.deleteScene invoked: id={}", id);
        try {
            return trashService.trashScene(CurrentUser.id(request), id).isPresent()
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    /**
     * One-time repair endpoint: recomputes word_count for every scene from its
     * stored HTML content using the same /\S+/ algorithm as the frontend.
     *
     * Needed because the autosave path previously omitted wordCount from the
     * PUT /scenes/{id}/content request, causing every editor-touched scene to
     * have word_count = 0 (Java int default when field is absent from JSON).
     *
     * Call once after deploying the fix:
     * curl -X POST http://localhost:8080/api/admin/recalculate-word-counts
     *
     * Returns { "updated": N } where N is the number of scenes processed.
     * Safe to call multiple times — idempotent.
     */
    @POST
    @Path("/admin/recalculate-word-counts")
    public Response recalculateWordCounts() {
        try {
            int updated = sceneDao.recalculateAllWordCounts();
            logger.info("recalculate-word-counts: updated {} scenes", updated);
            return Response.ok(Map.of("updated", updated)).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.error("Database error in SceneResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
