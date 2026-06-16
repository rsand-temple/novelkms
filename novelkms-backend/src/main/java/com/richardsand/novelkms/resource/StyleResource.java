package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.StyleDao;
import com.richardsand.novelkms.model.StyleDefaults;
import com.richardsand.novelkms.model.StyleDefinition;

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
 * Paragraph-style endpoints.
 *
 * <p>Globals (editable defaults spanning all projects):
 * <pre>
 *   GET    /api/styles/global               -> all global styles (roster order)
 *   GET    /api/styles/global/{key}         -> one global style
 *   PUT    /api/styles/global/{key}         -> update global definition
 *   POST   /api/styles/global/{key}/reset   -> reset to factory default
 * </pre>
 *
 * <p>Project overrides + resolved project stylesheet (PROJECT -> GLOBAL):
 * <pre>
 *   GET    /api/projects/{projectId}/styles         -> resolved stylesheet
 *   GET    /api/projects/{projectId}/styles/{key}   -> resolved single (check scope)
 *   PUT    /api/projects/{projectId}/styles/{key}   -> upsert project override
 *   DELETE /api/projects/{projectId}/styles/{key}   -> remove project override
 * </pre>
 *
 * <p>Book overrides + resolved book stylesheet (BOOK -> PROJECT -> GLOBAL):
 * <pre>
 *   GET    /api/books/{bookId}/styles               -> resolved stylesheet
 *   GET    /api/books/{bookId}/styles/{key}         -> resolved single (check scope)
 *   PUT    /api/books/{bookId}/styles/{key}         -> upsert book override
 *   DELETE /api/books/{bookId}/styles/{key}         -> remove book override
 * </pre>
 *
 * Body for PUT: { "definition": { fontFamily, fontSize, bold, italic,
 *                 firstLineIndent, textIndent, spacingBefore, spacingAfter } }.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StyleResource {
    private static final Logger logger = LoggerFactory.getLogger(StyleResource.class);

    private final StyleDao styleDao;

    @Inject
    public StyleResource(StyleDao styleDao) {
        this.styleDao = styleDao;
    }

    public static class DefinitionRequest {
        @JsonProperty public StyleDefinition definition;
    }

    // -------------------------------------------------------------------------
    // Global
    // -------------------------------------------------------------------------

    @GET
    @Path("/styles/global")
    public Response getAllGlobal() {
        try {
            return Response.ok(styleDao.getAllGlobal()).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/styles/global/{key}")
    public Response getGlobal(@PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return Response.ok(styleDao.getOrCreateGlobal(k)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/styles/global/{key}")
    public Response updateGlobal(@PathParam("key") String key, DefinitionRequest req) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        if (req == null || req.definition == null) return badRequest("definition is required");
        try {
            return Response.ok(styleDao.updateGlobal(k, req.definition)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/styles/global/{key}/reset")
    public Response resetGlobal(@PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return Response.ok(styleDao.resetGlobal(k)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Project
    // -------------------------------------------------------------------------

    @GET
    @Path("/projects/{projectId}/styles")
    public Response getProjectSheet(@PathParam("projectId") UUID projectId) {
        try {
            return Response.ok(styleDao.resolveAllForProject(projectId)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/projects/{projectId}/styles/{key}")
    public Response getProjectStyle(@PathParam("projectId") UUID projectId, @PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return Response.ok(styleDao.resolveForProject(projectId, k)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/projects/{projectId}/styles/{key}")
    public Response upsertProjectStyle(@PathParam("projectId") UUID projectId, @PathParam("key") String key,
            DefinitionRequest req) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        if (req == null || req.definition == null) return badRequest("definition is required");
        try {
            return Response.ok(styleDao.upsertProjectOverride(projectId, k, req.definition)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/projects/{projectId}/styles/{key}")
    public Response deleteProjectStyle(@PathParam("projectId") UUID projectId, @PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return styleDao.deleteProjectOverride(projectId, k)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Book
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/styles")
    public Response getBookSheet(@PathParam("bookId") UUID bookId) {
        try {
            return Response.ok(styleDao.resolveAllForBook(bookId)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/books/{bookId}/styles/{key}")
    public Response getBookStyle(@PathParam("bookId") UUID bookId, @PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return Response.ok(styleDao.resolveForBook(bookId, k)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{bookId}/styles/{key}")
    public Response upsertBookStyle(@PathParam("bookId") UUID bookId, @PathParam("key") String key,
            DefinitionRequest req) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        if (req == null || req.definition == null) return badRequest("definition is required");
        try {
            return Response.ok(styleDao.upsertBookOverride(bookId, k, req.definition)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{bookId}/styles/{key}")
    public Response deleteBookStyle(@PathParam("bookId") UUID bookId, @PathParam("key") String key) {
        String k = normalizeKey(key);
        if (k == null) return badKey();
        try {
            return styleDao.deleteBookOverride(bookId, k)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String normalizeKey(String raw) {
        if (raw == null) return null;
        String k = raw.trim().toLowerCase();
        return StyleDefaults.isValidKey(k) ? k : null;
    }

    private static Response badKey() {
        return badRequest("unknown style key; valid keys: " + StyleDefaults.STYLE_KEYS);
    }

    private static Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
    }

    private Response serverError(SQLException sqle) {
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(sqle.getMessage()).build();
    }
}
