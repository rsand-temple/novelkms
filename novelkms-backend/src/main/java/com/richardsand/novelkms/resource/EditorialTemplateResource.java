package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AiPromptTemplateDao;
import com.richardsand.novelkms.dao.AiPromptTemplateDao.Resolved;
import com.richardsand.novelkms.dao.AiPromptTemplateDao.TemplateType;

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
import jakarta.ws.rs.core.Response.Status;

/**
 * Editorial prompt template editing. The author can override the system default
 * developmental-editor persona at three independent scopes.
 *
 * <p>Endpoints mirror {@link MemoryTemplateResource}:
 * <ul>
 *   <li>{@code GET|PUT|DELETE /editorial-template/global}</li>
 *   <li>{@code GET|PUT|DELETE /projects/{id}/editorial-template}</li>
 *   <li>{@code GET|PUT|DELETE /books/{id}/editorial-template}</li>
 * </ul>
 * Each GET returns {@code { scope, content, hasOwnOverride }}. PUT requires
 * non-blank {@code content}. DELETE reverts to the next-most-specific value.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EditorialTemplateResource {

    private static final TemplateType TYPE = TemplateType.EDITORIAL;

    private final AiPromptTemplateDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public EditorialTemplateResource(AiPromptTemplateDao dao) {
        this.dao = dao;
    }

    public static class TemplateRequest {
        @JsonProperty public String content;
    }

    public static class View {
        @JsonProperty public String  scope;
        @JsonProperty public String  content;
        @JsonProperty public boolean hasOwnOverride;

        View(Resolved r) {
            this.scope          = r.scope();
            this.content        = r.content();
            this.hasOwnOverride = r.hasOwnOverride();
        }
    }

    // ── Global (user) ─────────────────────────────────────────────────────────

    @GET
    @Path("/editorial-template/global")
    public Response getGlobal() {
        return run(() -> Response.ok(new View(dao.resolveGlobal(TYPE, userId()))).build());
    }

    @PUT
    @Path("/editorial-template/global")
    public Response putGlobal(TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.upsertGlobal(TYPE, userId(), body.content.trim());
            return Response.ok(new View(dao.resolveGlobal(TYPE, userId()))).build();
        });
    }

    @DELETE
    @Path("/editorial-template/global")
    public Response deleteGlobal() {
        return run(() -> {
            dao.deleteGlobal(TYPE, userId());
            return Response.ok(new View(dao.resolveGlobal(TYPE, userId()))).build();
        });
    }

    // ── Project override ──────────────────────────────────────────────────────

    @GET
    @Path("/projects/{id}/editorial-template")
    public Response getProject(@PathParam("id") UUID projectId) {
        return run(() -> Response.ok(new View(dao.resolveForProject(TYPE, userId(), projectId))).build());
    }

    @PUT
    @Path("/projects/{id}/editorial-template")
    public Response putProject(@PathParam("id") UUID projectId, TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.setProject(TYPE, projectId, body.content.trim());
            return Response.ok(new View(dao.resolveForProject(TYPE, userId(), projectId))).build();
        });
    }

    @DELETE
    @Path("/projects/{id}/editorial-template")
    public Response deleteProject(@PathParam("id") UUID projectId) {
        return run(() -> {
            dao.clearProject(TYPE, projectId);
            return Response.ok(new View(dao.resolveForProject(TYPE, userId(), projectId))).build();
        });
    }

    // ── Book override ─────────────────────────────────────────────────────────

    @GET
    @Path("/books/{id}/editorial-template")
    public Response getBook(@PathParam("id") UUID bookId) {
        return run(() -> Response.ok(new View(dao.resolveForBook(TYPE, userId(), bookId))).build());
    }

    @PUT
    @Path("/books/{id}/editorial-template")
    public Response putBook(@PathParam("id") UUID bookId, TemplateRequest body) {
        if (isBlank(body)) return blankError();
        return run(() -> {
            dao.setBook(TYPE, bookId, body.content.trim());
            return Response.ok(new View(dao.resolveForBook(TYPE, userId(), bookId))).build();
        });
    }

    @DELETE
    @Path("/books/{id}/editorial-template")
    public Response deleteBook(@PathParam("id") UUID bookId) {
        return run(() -> {
            dao.clearBook(TYPE, bookId);
            return Response.ok(new View(dao.resolveForBook(TYPE, userId(), bookId))).build();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID userId() { return CurrentUser.id(request); }

    private static boolean isBlank(TemplateRequest body) {
        return body == null || body.content == null || body.content.isBlank();
    }

    private static Response blankError() {
        return Response.status(Status.BAD_REQUEST)
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
