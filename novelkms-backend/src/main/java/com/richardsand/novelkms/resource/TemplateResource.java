package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.TemplateDao;

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
 * Page-template endpoints.
 *
 * <p>Globals (the editable defaults that span all projects):
 * <pre>
 *   GET    /api/templates/global/{type}          -> resolved global (lazy-created)
 *   PUT    /api/templates/global/{type}          -> update global content
 *   POST   /api/templates/global/{type}/reset    -> reset global to factory default
 * </pre>
 *
 * <p>Per book (resolved value + override management):
 * <pre>
 *   GET    /api/books/{bookId}/templates/{type}  -> override if any, else global
 *   PUT    /api/books/{bookId}/templates/{type}  -> create/update the book override
 *   DELETE /api/books/{bookId}/templates/{type}  -> drop the override (revert to global)
 * </pre>
 *
 * <p>{@code type} is {@code cover} or {@code part} (case-insensitive). Inspect
 * the returned {@code scope} (GLOBAL vs BOOK) to know whether a book is
 * currently overriding.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateResource {
    private static final Logger logger = LoggerFactory.getLogger(TemplateResource.class);

    private final TemplateDao templateDao;

    @Inject
    public TemplateResource(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    /** Body: { "content": "<html>" }. A blank/empty template is permitted. */
    public static class ContentRequest {
        @JsonProperty public String content;
    }

    // -------------------------------------------------------------------------
    // Global templates
    // -------------------------------------------------------------------------

    @GET
    @Path("/templates/global/{type}")
    public Response getGlobal(@PathParam("type") String type) {
        String t = normalizeType(type);
        if (t == null) return badType();
        try {
            return Response.ok(templateDao.getOrCreateGlobal(t)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/templates/global/{type}")
    public Response updateGlobal(@PathParam("type") String type, ContentRequest req) {
        String t = normalizeType(type);
        if (t == null) return badType();
        if (req == null) return badRequest("content is required");
        try {
            return Response.ok(templateDao.updateGlobal(t, nullToEmpty(req.content))).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/templates/global/{type}/reset")
    public Response resetGlobal(@PathParam("type") String type) {
        String t = normalizeType(type);
        if (t == null) return badType();
        try {
            return Response.ok(templateDao.resetGlobal(t)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Per-book templates
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/templates/{type}")
    public Response getForBook(@PathParam("bookId") UUID bookId, @PathParam("type") String type) {
        String t = normalizeType(type);
        if (t == null) return badType();
        try {
            return Response.ok(templateDao.resolveForBook(bookId, t)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{bookId}/templates/{type}")
    public Response upsertBookOverride(@PathParam("bookId") UUID bookId,
                                       @PathParam("type") String type,
                                       ContentRequest req) {
        String t = normalizeType(type);
        if (t == null) return badType();
        if (req == null) return badRequest("content is required");
        try {
            return Response.ok(templateDao.upsertBookOverride(bookId, t, nullToEmpty(req.content))).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{bookId}/templates/{type}")
    public Response deleteBookOverride(@PathParam("bookId") UUID bookId, @PathParam("type") String type) {
        String t = normalizeType(type);
        if (t == null) return badType();
        try {
            return templateDao.deleteBookOverride(bookId, t)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Accepts "cover"/"part" in any case; returns the canonical type or null. */
    private static String normalizeType(String raw) {
        if (raw == null) return null;
        String up = raw.trim().toUpperCase();
        if (TemplateDao.TYPE_COVER.equals(up)) return TemplateDao.TYPE_COVER;
        if (TemplateDao.TYPE_PART.equals(up))  return TemplateDao.TYPE_PART;
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Response badType() {
        return badRequest("type must be one of: cover, part");
    }

    private static Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
    }

    private Response serverError(SQLException sqle) {
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
