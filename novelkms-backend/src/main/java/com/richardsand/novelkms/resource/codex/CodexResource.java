package com.richardsand.novelkms.resource.codex;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.model.codex.CodexCategory;

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

/**
 * Endpoints for the Codex: a Part-like world-building container that hangs off a
 * project (series scope) or a book (book scope). Its category chapters and entry
 * scenes reuse the chapter/scene tables and their existing endpoints — entries,
 * for example, are created with the standard POST /chapters/{id}/scenes.
 *
 * Ownership is enforced by TenantAuthorizationFilter on the path UUIDs
 * (projects/{id}, books/{id}, codex/{id}), so the handlers here do not re-check
 * the current user.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodexResource {
    private static final Logger    logger = LoggerFactory.getLogger(CodexResource.class);
    private final CodexDao         codexDao;
    private final CodexCategoryDao codexCategoryDao;
    private final ChapterDao       chapterDao;

    @Inject
    public CodexResource(CodexDao codexDao, CodexCategoryDao codexCategoryDao, ChapterDao chapterDao) {
        this.codexDao = codexDao;
        this.codexCategoryDao = codexCategoryDao;
        this.chapterDao = chapterDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateCodexRequest {
        @JsonProperty
        public String title;
    }

    public static class CreateCodexChapterRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String codexCategory;
    }

    public static class ReorderRequest {
        @JsonProperty
        public List<UUID> ids;
    }

    // -------------------------------------------------------------------------
    // Category lookup (drives dropdowns)
    // -------------------------------------------------------------------------

    @GET
    @Path("/codex/categories")
    public Response listCategories() {
        logger.debug("CodexResource.listCategories invoked");
        try {
            List<CodexCategory> categories = codexCategoryDao.findAll();
            return Response.ok(categories).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Project-scoped codex
    // -------------------------------------------------------------------------

    @GET
    @Path("/projects/{projectId}/codex")
    public Response getProjectCodex(@PathParam("projectId") UUID projectId) {
        logger.debug("CodexResource.getProjectCodex invoked: projectId={}", projectId);
        try {
            return codexDao.findByProjectId(projectId)
                    .map(cx -> Response.ok(cx).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/projects/{projectId}/codex")
    public Response createProjectCodex(@PathParam("projectId") UUID projectId, CreateCodexRequest req) {
        logger.info("CodexResource.createProjectCodex invoked: projectId={}", projectId);
        try {
            if (codexDao.findByProjectId(projectId).isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("This project already has a codex").build();
            }
            Codex codex = codexDao.createForProject(projectId, req != null ? req.title : null);
            seedDefaultChapters(codex.getId());
            return Response.status(Response.Status.CREATED).entity(codex).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Book-scoped codex
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/codex")
    public Response getBookCodex(@PathParam("bookId") UUID bookId) {
        logger.debug("CodexResource.getBookCodex invoked: bookId={}", bookId);
        try {
            return codexDao.findByBookId(bookId)
                    .map(cx -> Response.ok(cx).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/books/{bookId}/codex")
    public Response createBookCodex(@PathParam("bookId") UUID bookId, CreateCodexRequest req) {
        logger.info("CodexResource.createBookCodex invoked: bookId={}", bookId);
        try {
            if (codexDao.findByBookId(bookId).isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("This book already has a codex").build();
            }
            Codex codex = codexDao.createForBook(bookId, req != null ? req.title : null);
            seedDefaultChapters(codex.getId());
            return Response.status(Response.Status.CREATED).entity(codex).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Codex by id
    // -------------------------------------------------------------------------

    @GET
    @Path("/codex/{id}")
    public Response getCodex(@PathParam("id") UUID id) {
        logger.debug("CodexResource.getCodex invoked: id={}", id);
        try {
            return codexDao.findById(id)
                    .map(cx -> Response.ok(cx).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/codex/{id}")
    public Response deleteCodex(@PathParam("id") UUID id) {
        logger.info("CodexResource.deleteCodex invoked: id={}", id);
        try {
            return codexDao.delete(id)
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Category chapters within a codex
    // -------------------------------------------------------------------------

    @GET
    @Path("/codex/{codexId}/chapters")
    public Response listCodexChapters(@PathParam("codexId") UUID codexId) {
        logger.debug("CodexResource.listCodexChapters invoked: codexId={}", codexId);
        try {
            List<Chapter> chapters = chapterDao.findByCodexId(codexId);
            return Response.ok(chapters).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/codex/{codexId}/chapters")
    public Response createCodexChapter(@PathParam("codexId") UUID codexId, CreateCodexChapterRequest req) {
        logger.info("CodexResource.createCodexChapter invoked: codexId={}, category={}", codexId, req == null ? null : req.codexCategory);
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Optional<Codex> codex = codexDao.findById(codexId);
            if (codex.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Codex not found").build();
            }
            Chapter chapter = chapterDao.createCodexChapter(codexId, req.codexCategory, req.title);
            return Response.status(Response.Status.CREATED).entity(chapter).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/codex/{codexId}/chapters/reorder")
    public Response reorderCodexChapters(@PathParam("codexId") UUID codexId, ReorderRequest req) {
        logger.info("CodexResource.reorderCodexChapters invoked: codexId={}, count={}", codexId, req == null || req.ids == null ? 0 : req.ids.size());
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ids is required").build();
        }
        try {
            chapterDao.reorderInCodex(codexId, req.ids);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Seeds one category chapter per default codex_category, titled with the
     * category label, so a new codex opens with a sensible structure.
     */
    private void seedDefaultChapters(UUID codexId) throws SQLException {
        for (CodexCategory category : codexCategoryDao.findDefaults()) {
            chapterDao.createCodexChapter(codexId, category.getCategoryKey(), category.getLabel());
        }
    }

    private Response serverError(SQLException sqle) {
        logger.error("Database error in CodexResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
