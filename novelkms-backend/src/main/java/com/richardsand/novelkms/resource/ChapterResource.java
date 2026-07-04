package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.chapter.Chapter;
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
public class ChapterResource {
    private static final Logger logger = LoggerFactory.getLogger(ChapterResource.class);
    private final ChapterDao    chapterDao;
    private final SceneDao      sceneDao;

    private final TrashService  trashService;

    @Inject
    public ChapterResource(ChapterDao chapterDao, SceneDao sceneDao, TrashService trashService) {
        this.chapterDao = chapterDao;
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
        public String subtitle;
        @JsonProperty
        public String notes;
        /** Optional — omit to place chapter directly under the book. */
        @JsonProperty
        public UUID   partId;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String subtitle;
        @JsonProperty
        public String notes;
        @JsonProperty
        public Boolean resetsNumbering;
    }

    /**
     * Shared by both reorder endpoints.
     * Body: { "ids": ["uuid1", "uuid2", ...] } in the desired display order.
     */
    public static class ReorderRequest {
        @JsonProperty
        public List<UUID> ids;
    }

    public static class MoveChapterRequest {
        @JsonProperty("partId")
        public UUID       partId;
        @JsonProperty("sourceIds")
        public List<UUID> sourceIds = List.of();
        @JsonProperty("targetIds")
        public List<UUID> targetIds = List.of();
    }

    public static class MoveSceneRequest {
        @JsonProperty("chapterId")
        public UUID       chapterId;
        @JsonProperty("sourceIds")
        public List<UUID> sourceIds = List.of();
        @JsonProperty("targetIds")
        public List<UUID> targetIds = List.of();
    }

    // -------------------------------------------------------------------------
    // Endpoints — chapters
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/chapters")
    public Response listChapters(@PathParam("bookId") UUID bookId) {
        logger.debug("ChapterResource.listChapters invoked: bookId={}", bookId);
        try {
            List<Chapter> chapters = chapterDao.findByBookId(bookId);
            return Response.ok(chapters).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/chapters/{id}")
    public Response getChapter(@PathParam("id") UUID id) {
        logger.debug("ChapterResource.getChapter invoked: id={}", id);
        try {
            return chapterDao.findById(id)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/books/{bookId}/chapters")
    public Response createChapter(@PathParam("bookId") UUID bookId, CreateRequest req) {
        logger.info("ChapterResource.createChapter invoked: bookId={}, partId={}", bookId, req == null ? null : req.partId);
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Chapter chapter = chapterDao.create(bookId, req.partId, req.title, req.subtitle, req.notes);
            return Response.status(Response.Status.CREATED).entity(chapter).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/chapters/{id}")
    public Response updateChapter(@PathParam("id") UUID id, UpdateRequest req) {
        logger.info("ChapterResource.updateChapter invoked: id={}", id);
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body is required").build();
        }
        if (req.resetsNumbering == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("resetsNumbering is required").build();
        }
        // Unlike create, a blank title is a valid state on update — the nav tree
        // and EditorPanel both fall back to "Chapter N" when title is empty.
        // Never persist null, since the title column is NOT NULL.
        String title = req.title != null ? req.title : "";
        try {
            return chapterDao.update(id, title, req.subtitle, req.notes, req.resetsNumbering)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/chapters/{id}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveChapter(@PathParam("id") UUID id, MoveChapterRequest req) {
        logger.info("ChapterResource.moveChapter invoked: id={}, partId={}", id, req == null ? null : req.partId);
        try {
            chapterDao.moveChapter(id, req.partId, req.sourceIds, req.targetIds);
            return Response.ok().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/scenes/{id}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveScene(@PathParam("id") UUID id, MoveSceneRequest req) {
        logger.info("ChapterResource.moveScene invoked: id={}, chapterId={}", id, req == null ? null : req.chapterId);
        try {
            sceneDao.moveScene(id, req.chapterId, req.sourceIds, req.targetIds);
            return Response.ok().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/chapters/{id}")
    public Response deleteChapter(@PathParam("id") UUID id, @Context ContainerRequestContext request) {
        logger.info("ChapterResource.deleteChapter invoked: id={}", id);
        try {
            return trashService.trashChapter(CurrentUser.id(request), id).isPresent()
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    /**
     * PUT /api/books/{bookId}/chapters/reorder
     *
     * Reorders all chapters within a book. The request body must contain the
     * complete ordered list of chapter IDs for this book. Chapters not present
     * in the list are unaffected (their display_order is not touched), which
     * means the caller should always send the full sibling list.
     */
    @PUT
    @Path("/books/{bookId}/chapters/reorder")
    public Response reorderChapters(@PathParam("bookId") UUID bookId, ReorderRequest req) {
        logger.info("ChapterResource.reorderChapters invoked: bookId={}, count={}", bookId, req == null || req.ids == null ? 0 : req.ids.size());
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ids is required").build();
        }
        try {
            chapterDao.reorderInBook(bookId, req.ids);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Endpoints — scene ordering (lives here because the path is
    // /chapters/{id}/scenes/reorder and ChapterResource owns /chapters/*)
    // -------------------------------------------------------------------------

    /**
     * PUT /api/chapters/{chapterId}/scenes/reorder
     *
     * Reorders all scenes within a chapter. The request body must contain the
     * complete ordered list of scene IDs for this chapter.
     */
    @PUT
    @Path("/chapters/{chapterId}/scenes/reorder")
    public Response reorderScenes(@PathParam("chapterId") UUID chapterId, ReorderRequest req) {
        logger.info("ChapterResource.reorderScenes invoked: chapterId={}, count={}", chapterId, req == null || req.ids == null ? 0 : req.ids.size());
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ids is required").build();
        }
        try {
            sceneDao.reorderInChapter(chapterId, req.ids);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.error("Database error in ChapterResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
