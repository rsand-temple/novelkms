package com.richardsand.novelkms.resource.codex;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.model.codex.CodexCategory;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexSchema;
import com.richardsand.novelkms.service.CodexFieldUsageService;

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
 * (projects/{id}, books/{id}, codex/{id}, and codex/types/{typeId} where typeId
 * is a category chapter id mapped to ownsChapter), so the handlers here do not
 * re-check the current user.
 *
 * <p>The Extensible Codex type-editor write path (E4) lives here rather than in
 * a dedicated resource: this class is {@code @Path("/")} and already owns
 * {@code GET /codex/types/{typeId}} (E2), so a separate {@code @Path("/codex/types")}
 * resource would claim that prefix and shadow the existing method (Jersey
 * resolves the class-level prefix first). Field identity in these endpoints is
 * the immutable {@code field_key}, which is what the client already holds and is
 * unique within a Type.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodexResource {
    private static final Logger    logger = LoggerFactory.getLogger(CodexResource.class);

    private static final Set<String> INPUT_TYPES = Set.of("SHORT_TEXT", "LONG_TEXT", "SELECT");

    private final CodexDao                codexDao;
    private final CodexCategoryDao        codexCategoryDao;
    private final ChapterDao              chapterDao;
    private final CodexTypeDao            codexTypeDao;
    private final CodexTypeFieldDao       codexTypeFieldDao;
    private final CodexFieldUsageService  codexFieldUsageService;

    @Inject
    public CodexResource(CodexDao codexDao, CodexCategoryDao codexCategoryDao, ChapterDao chapterDao,
            CodexTypeDao codexTypeDao, CodexTypeFieldDao codexTypeFieldDao,
            CodexFieldUsageService codexFieldUsageService) {
        this.codexDao = codexDao;
        this.codexCategoryDao = codexCategoryDao;
        this.chapterDao = chapterDao;
        this.codexTypeDao = codexTypeDao;
        this.codexTypeFieldDao = codexTypeFieldDao;
        this.codexFieldUsageService = codexFieldUsageService;
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

    /** Create an author-defined Type (E4). */
    public static class CreateTypeRequest {
        @JsonProperty
        public String name;
        @JsonProperty
        public String description;
    }

    /** Rename a Type and/or edit its description (E4). */
    public static class UpdateTypeRequest {
        @JsonProperty
        public String name;
        @JsonProperty
        public String description;
    }

    /**
     * Add or update a field (E4). Boxed {@code feedsAi} so an omitted value can
     * default to TRUE rather than silently becoming false.
     */
    public static class FieldRequest {
        @JsonProperty
        public String       label;
        @JsonProperty
        public String       inputType;
        @JsonProperty
        public List<String> options;
        @JsonProperty
        public String       help;
        @JsonProperty
        public Boolean      feedsAi;
    }

    /** Reorder a Type's fields by their immutable keys (E4). */
    public static class FieldOrderRequest {
        @JsonProperty
        public List<String> fieldKeys;
    }

    // -------------------------------------------------------------------------
    // Category lookup (drives dropdowns; seed + AI-promotion mapping source)
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
    // Per-instance Codex Type (category chapter + its own field set)
    //
    // typeId is a category chapter id. Ownership is enforced by
    // TenantAuthorizationFilter, which maps the "types" path segment to
    // ownsChapter (a codex chapter resolves ownership through its codex's
    // project/book). Reads and writes are guarded to live codex Types, so a
    // manuscript chapter, a trashed Type, or an unknown id yields 404.
    // -------------------------------------------------------------------------

    @GET
    @Path("/codex/types/{typeId}")
    public Response getCodexType(@PathParam("typeId") UUID typeId) {
        logger.debug("CodexResource.getCodexType invoked: typeId={}", typeId);
        try {
            return codexTypeDao.findType(typeId)
                    .map(type -> Response.ok(type).build())
                    .orElse(notFound());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/codex/types/{typeId}")
    public Response updateCodexType(@PathParam("typeId") UUID typeId, UpdateTypeRequest req) {
        logger.info("CodexResource.updateCodexType invoked: typeId={}", typeId);
        if (req == null || req.name == null || req.name.isBlank()) {
            return badRequest("name is required");
        }
        try {
            return codexTypeDao.updateHeader(typeId, req.name.trim(), blankToNull(req.description))
                    .map(type -> Response.ok(type).build())
                    .orElse(notFound());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/codex/{codexId}/types")
    public Response createCodexType(@PathParam("codexId") UUID codexId, CreateTypeRequest req) {
        logger.info("CodexResource.createCodexType invoked: codexId={}", codexId);
        if (req == null || req.name == null || req.name.isBlank()) {
            return badRequest("name is required");
        }
        try {
            if (codexDao.findById(codexId).isEmpty()) {
                return badRequest("Codex not found");
            }
            var type = codexTypeDao.createType(codexId, req.name.trim(), blankToNull(req.description));
            return Response.status(Response.Status.CREATED).entity(type).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/codex/types/{typeId}/fields")
    public Response addTypeField(@PathParam("typeId") UUID typeId, FieldRequest req) {
        logger.info("CodexResource.addTypeField invoked: typeId={}", typeId);
        Response invalid = validateField(req);
        if (invalid != null) {
            return invalid;
        }
        try {
            if (codexTypeDao.findType(typeId).isEmpty()) {
                return notFound();
            }
            CodexField field = codexTypeFieldDao.addField(typeId, req.label.trim(), req.inputType,
                    req.options, blankToNull(req.help), feedsAiOrDefault(req));
            return Response.status(Response.Status.CREATED).entity(field).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/codex/types/{typeId}/fields/{fieldKey}")
    public Response updateTypeField(@PathParam("typeId") UUID typeId,
            @PathParam("fieldKey") String fieldKey, FieldRequest req) {
        logger.info("CodexResource.updateTypeField invoked: typeId={}, fieldKey={}", typeId, fieldKey);
        Response invalid = validateField(req);
        if (invalid != null) {
            return invalid;
        }
        try {
            return codexTypeFieldDao.updateField(typeId, fieldKey, req.label.trim(), req.inputType,
                    req.options, blankToNull(req.help), feedsAiOrDefault(req))
                    .map(field -> Response.ok(field).build())
                    .orElse(notFound());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/codex/types/{typeId}/fields/order")
    public Response reorderTypeFields(@PathParam("typeId") UUID typeId, FieldOrderRequest req) {
        logger.info("CodexResource.reorderTypeFields invoked: typeId={}, count={}",
                typeId, req == null || req.fieldKeys == null ? 0 : req.fieldKeys.size());
        if (req == null || req.fieldKeys == null || req.fieldKeys.isEmpty()) {
            return badRequest("fieldKeys is required");
        }
        try {
            if (codexTypeDao.findType(typeId).isEmpty()) {
                return notFound();
            }
            codexTypeFieldDao.reorderFields(typeId, req.fieldKeys);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Type field soft-remove / restore / usage (E6). Field identity is the
    // immutable field key. Soft-remove hides a field from the form without
    // touching stored entry values; restore re-shows it in its original slot.
    // The usage read lists every field (active and removed) with its entry
    // count so the editor can warn before removal and drive the "Removed
    // fields" area. All three hang off the tenant-authorized /codex/types/{id}
    // segment; the bodyless DELETE and restore POST do not trip the sensitive-
    // body inspector.
    // -------------------------------------------------------------------------

    @DELETE
    @Path("/codex/types/{typeId}/fields/{fieldKey}")
    public Response removeTypeField(@PathParam("typeId") UUID typeId,
            @PathParam("fieldKey") String fieldKey) {
        logger.info("CodexResource.removeTypeField invoked: typeId={}, fieldKey={}", typeId, fieldKey);
        try {
            return codexTypeFieldDao.softRemoveField(typeId, fieldKey)
                    ? Response.noContent().build()
                    : notFound();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/codex/types/{typeId}/fields/{fieldKey}/restore")
    public Response restoreTypeField(@PathParam("typeId") UUID typeId,
            @PathParam("fieldKey") String fieldKey) {
        logger.info("CodexResource.restoreTypeField invoked: typeId={}, fieldKey={}", typeId, fieldKey);
        try {
            return codexTypeFieldDao.restoreField(typeId, fieldKey)
                    .map(field -> Response.ok(field).build())
                    .orElse(notFound());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/codex/types/{typeId}/fields/usage")
    public Response getTypeFieldUsage(@PathParam("typeId") UUID typeId) {
        logger.debug("CodexResource.getTypeFieldUsage invoked: typeId={}", typeId);
        try {
            if (codexTypeDao.findType(typeId).isEmpty()) {
                return notFound();
            }
            return Response.ok(codexFieldUsageService.usage(typeId)).build();
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
     * category label, so a new codex opens with a sensible structure. Each
     * seeded Type also gets its own per-instance field rows (E7), copied verbatim
     * from the category's master schema — so a brand-new codex owns its schema in
     * {@code codex_type_field} exactly as the V42-backfilled instances do, rather
     * than borrowing the system-global schema that the E3 cutover retired.
     * Schema-less default categories (PLOT, WORLD, TIMELINE, CANON, NOTES) seed
     * no field rows and remain plain title-plus-body types.
     */
    private void seedDefaultChapters(UUID codexId) throws SQLException {
        for (CodexCategory category : codexCategoryDao.findDefaults()) {
            Chapter type = chapterDao.createCodexChapter(codexId, category.getCategoryKey(), category.getLabel());
            CodexSchema schema = category.getSchema();
            if (schema != null && schema.getFields() != null && !schema.getFields().isEmpty()) {
                codexTypeFieldDao.seedFields(type.getId(), schema.getFields());
            }
        }
    }

    /**
     * Shared validation for add/update field: label required; input type must be
     * one of the three supported styles. Returns a 400 Response to short-circuit,
     * or null when the request is valid.
     */
    private Response validateField(FieldRequest req) {
        if (req == null || req.label == null || req.label.isBlank()) {
            return badRequest("label is required");
        }
        if (req.inputType == null || !INPUT_TYPES.contains(req.inputType)) {
            return badRequest("inputType must be one of SHORT_TEXT, LONG_TEXT, SELECT");
        }
        return null;
    }

    private static boolean feedsAiOrDefault(FieldRequest req) {
        return req.feedsAi == null || req.feedsAi;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private Response serverError(SQLException sqle) {
        logger.error("Database error in CodexResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
