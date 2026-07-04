package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.chapter.Chapter;

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

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PartResource {
    private static final Logger logger = LoggerFactory.getLogger(PartResource.class);
    private final PartDao       partDao;
    private final ChapterDao    chapterDao;

    @Inject
    public PartResource(PartDao partDao, ChapterDao chapterDao) {
        this.partDao = partDao;
        this.chapterDao = chapterDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreatePartRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String subtitle;
        @JsonProperty
        public String notes;
    }

    public static class UpdatePartRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String subtitle;
        @JsonProperty
        public String notes;
    }

    public static class CreateChapterRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String notes;
    }

    public static class ReorderRequest {
        @JsonProperty
        public List<UUID> ids;
    }

    // -------------------------------------------------------------------------
    // Part CRUD
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/parts")
    public Response listParts(@PathParam("bookId") UUID bookId) {
        logger.debug("PartResource.listParts invoked: bookId={}", bookId);
        try {
            List<Part> parts = partDao.findByBookId(bookId);
            return Response.ok(parts).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/books/{bookId}/parts")
    public Response createPart(@PathParam("bookId") UUID bookId, CreatePartRequest req) {
        logger.info("PartResource.createPart invoked: bookId={}", bookId);
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Part part = partDao.create(bookId, req.title, req.subtitle, req.notes);
            return Response.status(Response.Status.CREATED).entity(part).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/parts/{id}")
    public Response getPart(@PathParam("id") UUID id) {
        logger.debug("PartResource.getPart invoked: id={}", id);
        try {
            return partDao.findById(id)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/parts/{id}")
    public Response updatePart(@PathParam("id") UUID id, UpdatePartRequest req) {
        logger.info("PartResource.updatePart invoked: id={}", id);
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body is required").build();
        }
        // Unlike create, a blank title is a valid state on update — the nav tree
        // and EditorPanel both fall back to "Part {Roman numeral}" when title is empty.
        // Never persist null, since the title column is NOT NULL.
        String title = req.title != null ? req.title : "";
        try {
            return partDao.update(id, title, req.subtitle, req.notes)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/parts/{id}")
    public Response deletePart(@PathParam("id") UUID id) {
        logger.info("PartResource.deletePart invoked: id={}", id);
        try {
            return partDao.delete(id)
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{bookId}/parts/reorder")
    public Response reorderParts(@PathParam("bookId") UUID bookId, ReorderRequest req) {
        logger.info("PartResource.reorderParts invoked: bookId={}, count={}", bookId, req == null || req.ids == null ? 0 : req.ids.size());
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ids is required").build();
        }
        try {
            partDao.reorderInBook(bookId, req.ids);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Word count
    // -------------------------------------------------------------------------

    /**
     * Returns the total word count for a part: the part heading words, all
     * scene content in the part's chapters, and all chapter heading words.
     * Used by the editor status bar when a part is selected.
     *
     * Returns { "wordCount": N }
     */
    @GET
    @Path("/parts/{id}/word-count")
    public Response getWordCount(@PathParam("id") UUID id) {
        logger.debug("PartResource.getWordCount invoked: id={}", id);
        try {
            int count = partDao.getTotalWordCount(id);
            return Response.ok(Map.of("wordCount", count)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Chapters within a part
    // -------------------------------------------------------------------------

    @GET
    @Path("/parts/{partId}/chapters")
    public Response listPartChapters(@PathParam("partId") UUID partId) {
        logger.debug("PartResource.listPartChapters invoked: partId={}", partId);
        try {
            List<Chapter> chapters = chapterDao.findByPartId(partId);
            return Response.ok(chapters).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/parts/{partId}/chapters")
    public Response createPartChapter(@PathParam("partId") UUID partId, CreateChapterRequest req) {
        logger.info("PartResource.createPartChapter invoked: partId={}", partId);
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Optional<Part> part = partDao.findById(partId);
            if (part.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Part not found").build();
            }
            Chapter chapter = chapterDao.create(part.get().getBookId(), partId, req.title, null, req.notes);
            return Response.status(Response.Status.CREATED).entity(chapter).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/parts/{partId}/chapters/reorder")
    public Response reorderPartChapters(@PathParam("partId") UUID partId, ReorderRequest req) {
        logger.info("PartResource.reorderPartChapters invoked: partId={}, count={}", partId, req == null || req.ids == null ? 0 : req.ids.size());
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ids is required").build();
        }
        try {
            chapterDao.reorderInPart(partId, req.ids);
            return Response.ok().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.error("Database error in PartResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}