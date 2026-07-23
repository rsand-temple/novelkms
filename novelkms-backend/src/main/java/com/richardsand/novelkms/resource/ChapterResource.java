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
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.book.OutlineRef;
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
        public String  title;
        @JsonProperty
        public String  subtitle;
        @JsonProperty
        public String  notes;
        /** Optional — omit to place chapter directly under the book. */
        @JsonProperty
        public UUID    partId;
        /**
         * Optional insert anchor. Omit to append.
         *
         * <p>For a direct-book chapter this is an outline item and may be a PART
         * or a CHAPTER — inserting before Part I is exactly how a prologue gets
         * made. For a part-contained chapter it is a sibling chapter.
         */
        @JsonProperty
        public UUID    anchorId;
        /** true = insert before the anchor, false/omitted = after it. */
        @JsonProperty
        public Boolean before;
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
     * Scene reordering within a chapter — a single-table list, so bare IDs are
     * enough here (unlike the book outline, which spans two tables).
     * Body: { "ids": ["uuid1", "uuid2", ...] } in the desired display order.
     */
    public static class ReorderRequest {
        @JsonProperty
        public List<UUID> ids;
    }

    /**
     * A chapter move names BOTH containers, and carries typed items rather than
     * bare UUIDs.
     *
     * <p>Since V40 the book-level container is the outline — parts and
     * direct-book chapters interleaved in one display_order sequence — so a list
     * of bare IDs is no longer sufficient to renumber it: the writer cannot tell
     * a part row from a chapter row, and the two live in different tables. A
     * part's chapter list is still a plain chapter list, but the payload is
     * uniform for both so one code path serves either direction.
     */
    public static class MoveChapterRequest {
        /** Target container: a part, or null for the book outline. */
        @JsonProperty("partId")
        public UUID             partId;
        /** Source container: the part it came from, or null for the book outline. */
        @JsonProperty("sourcePartId")
        public UUID             sourcePartId;
        /** Source container contents AFTER removal. */
        @JsonProperty("sourceItems")
        public List<OutlineRef> sourceItems = List.of();
        /** Target container contents AFTER insertion (includes the moved chapter). */
        @JsonProperty("targetItems")
        public List<OutlineRef> targetItems = List.of();
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

    /**
     * GET /api/books/{bookId}/scratchpad
     *
     * <p>The book's Scratchpad — a holding pen for scenes that are not part of
     * the manuscript. Get-or-create: there is no separate POST, because a
     * Scratchpad is a structural fixture of every book rather than something the
     * author decides to add, and V43 deliberately ships no backfill. The first
     * read after upgrading creates the row.
     *
     * <p>Scenes are created inside it with the ordinary
     * {@code POST /api/chapters/{chapterId}/scenes}, and moved in and out of the
     * manuscript with the ordinary {@code PUT /api/scenes/{id}/move} — the
     * Scratchpad needs no scene endpoints of its own.
     *
     * <p>Tenant authorization comes from the {@code books/{bookId}} segment, so
     * a Scratchpad can only ever be created under a book the caller owns.
     */
    @GET
    @Path("/books/{bookId}/scratchpad")
    public Response getScratchpad(@PathParam("bookId") UUID bookId) {
        logger.debug("ChapterResource.getScratchpad invoked: bookId={}", bookId);
        try {
            return Response.ok(chapterDao.getOrCreateScratchpad(bookId)).build();
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
            boolean before  = Boolean.TRUE.equals(req.before);
            Chapter chapter = (req.anchorId != null)
                    ? chapterDao.createRelativeTo(bookId, req.partId, req.title, req.subtitle, req.notes,
                            req.anchorId, before)
                    : chapterDao.create(bookId, req.partId, req.title, req.subtitle, req.notes);
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
            if (chapterDao.isScratchpad(id)) {
                return scratchpadNotAllowed("The Scratchpad cannot be renamed or edited.");
            }
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
        logger.info("ChapterResource.moveChapter invoked: id={}, sourcePartId={}, partId={}",
                id, req == null ? null : req.sourcePartId, req == null ? null : req.partId);
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body is required").build();
        }
        try {
            // The Scratchpad sits outside the book outline entirely — it has no
            // position to move to. Its scenes move freely; the container does not.
            if (chapterDao.isScratchpad(id)) {
                return scratchpadNotAllowed("The Scratchpad cannot be moved.");
            }
            chapterDao.moveChapter(id,
                    req.sourcePartId, req.sourceItems,
                    req.partId, req.targetItems);
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
            // TrashDao already refuses to stamp a Scratchpad, but that refusal is
            // indistinguishable from "already gone" (204). Reject explicitly here
            // so the author gets told why instead of watching nothing happen.
            if (chapterDao.isScratchpad(id)) {
                return scratchpadNotAllowed(
                        "The Scratchpad cannot be deleted. Delete the scenes inside it instead.");
            }
            return trashService.trashChapter(CurrentUser.id(request), id).isPresent()
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    /**
     * The Scratchpad is a fixture of its book, not a chapter: it cannot be
     * renamed, moved, or deleted. Structural requests against it are rejected
     * with a stable {@code not_scratchpad_operation} code so the frontend can
     * distinguish this from a validation failure.
     */
    private Response scratchpadNotAllowed(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "not_scratchpad_operation", "message", message))
                .build();
    }

    // Book-level chapter reordering used to live here as
    // PUT /books/{bookId}/chapters/reorder. It was removed in V40: parts and
    // direct-book chapters now share one display_order sequence, so renumbering
    // the chapters 0..n-1 on their own would land them straight on top of the
    // parts interleaved among them. Book-level ordering is now a single
    // operation over both — see BookOutlineResource.

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
