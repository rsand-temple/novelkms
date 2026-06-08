package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Chapter;

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
public class ChapterResource {
    private static final Logger logger = LoggerFactory.getLogger(ChapterResource.class);
    private final ChapterDao    chapterDao;
    private final SceneDao      sceneDao;

    @Inject
    public ChapterResource(ChapterDao chapterDao, SceneDao sceneDao) {
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
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

    // -------------------------------------------------------------------------
    // Endpoints — chapters
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/chapters")
    public Response listChapters(@PathParam("bookId") UUID bookId) {
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
        try {
            return chapterDao.findById(id)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/books/{bookId}/chapters")
    public Response createChapter(@PathParam("bookId") UUID bookId, CreateRequest req) {
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
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            return chapterDao.update(id, req.title, req.subtitle, req.notes)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }
    
    @PUT
    @Path("/chapters/{id}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveChapter(@PathParam("id") UUID id, MoveChapterRequest req) {
        try {
            chapterDao.moveChapter(id, req.partId, req.sourceIds, req.targetIds);
            return Response.ok().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/chapters/{id}")
    public Response deleteChapter(@PathParam("id") UUID id) {
        try {
            return chapterDao.delete(id)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
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
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}