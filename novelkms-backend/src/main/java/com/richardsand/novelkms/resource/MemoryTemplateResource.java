package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.MemoryTemplateDao;
import com.richardsand.novelkms.dao.MemoryTemplateDao.Resolved;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Memory-document template editing — the section structure the AI fills in when
 * generating a chapter's memory document. The author can override it at three
 * independent scopes over the non-editable system default.
 *
 * <p>Endpoints mirror {@link AiFormInstructionsResource}:
 * <ul>
 *   <li>{@code /memory-template/global} — the user global (USER -&gt; SYSTEM),
 *       owned by the caller via {@link CurrentUser}.</li>
 *   <li>{@code /projects/{id}/memory-template} — project override.</li>
 *   <li>{@code /books/{id}/memory-template} — book override.</li>
 * </ul>
 * The {@code projects/{id}} and {@code books/{id}} segments are authorized by the
 * tenant filter. Each GET returns {@code scope}, {@code content}, and
 * {@code hasOwnOverride}; PUT requires non-blank text; clearing an override is
 * DELETE, which returns the value the scope now falls back to.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MemoryTemplateResource {

    private final MemoryTemplateDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public MemoryTemplateResource(MemoryTemplateDao dao) {
        this.dao = dao;
    }

    /** Request body for PUT. */
    public static class TemplateRequest {
        @JsonProperty
        public String content;
    }

    /** Response view returned by every endpoint. */
    public static class View {
        @JsonProperty public String  scope;
        @JsonProperty public String  content;
        @JsonProperty public boolean hasOwnOverride;

        View(Resolved r) {
            this.scope = r.scope();
            this.content = r.content();
            this.hasOwnOverride = r.hasOwnOverride();
        }
    }

    private UUID u() {
        return CurrentUser.id(request);
    }

    // ── Global (user) ─────────────────────────────────────────────────────────

    @GET
    @Path("/memory-template/global")
    public Response getGlobal() {
        return run(() -> Response.ok(new View(dao.resolveGlobal(u()))).build());
    }

    @PUT
    @Path("/memory-template/global")
    public Response putGlobal(TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.upsertGlobal(u(), body.content.trim());
            return Response.ok(new View(dao.resolveGlobal(u()))).build();
        });
    }

    @DELETE
    @Path("/memory-template/global")
    public Response deleteGlobal() {
        return run(() -> {
            dao.deleteGlobal(u());
            return Response.ok(new View(dao.resolveGlobal(u()))).build();
        });
    }

    // ── Project override ──────────────────────────────────────────────────────

    @GET
    @Path("/projects/{id}/memory-template")
    public Response getProject(@PathParam("id") UUID projectId) {
        return run(() -> Response.ok(new View(dao.resolveForProject(u(), projectId))).build());
    }

    @PUT
    @Path("/projects/{id}/memory-template")
    public Response putProject(@PathParam("id") UUID projectId, TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.setProject(projectId, body.content.trim());
            return Response.ok(new View(dao.resolveForProject(u(), projectId))).build();
        });
    }

    @DELETE
    @Path("/projects/{id}/memory-template")
    public Response deleteProject(@PathParam("id") UUID projectId) {
        return run(() -> {
            dao.clearProject(projectId);
            return Response.ok(new View(dao.resolveForProject(u(), projectId))).build();
        });
    }

    // ── Book override ─────────────────────────────────────────────────────────

    @GET
    @Path("/books/{id}/memory-template")
    public Response getBook(@PathParam("id") UUID bookId) {
        return run(() -> Response.ok(new View(dao.resolveForBook(u(), bookId))).build());
    }

    @PUT
    @Path("/books/{id}/memory-template")
    public Response putBook(@PathParam("id") UUID bookId, TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.setBook(bookId, body.content.trim());
            return Response.ok(new View(dao.resolveForBook(u(), bookId))).build();
        });
    }

    @DELETE
    @Path("/books/{id}/memory-template")
    public Response deleteBook(@PathParam("id") UUID bookId) {
        return run(() -> {
            dao.clearBook(bookId);
            return Response.ok(new View(dao.resolveForBook(u(), bookId))).build();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isBlank(TemplateRequest body) {
        return body == null || body.content == null || body.content.isBlank();
    }

    private static Response blankError() {
        return Response.status(400)
                .entity(java.util.Map.of("error", "bad_request",
                        "message", "content must not be blank; use DELETE to remove an override."))
                .build();
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (SQLException e) {
            return Response.serverError().entity(java.util.Map.of("error", "server_error")).build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
