package com.richardsand.novelkms.resource.ai;

import java.sql.SQLException;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.ai.AiFormInstructionsDao;
import com.richardsand.novelkms.dao.ai.AiFormInstructionsDao.Resolved;
import com.richardsand.novelkms.resource.EditorSettingsResource;

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
 * AI review <em>form</em> instructions — the editorial persona/constraints the
 * author can edit at three independent scopes. The constant <em>functional</em>
 * block (the JSON output contract) is not editable and is not exposed here.
 *
 * <p>
 * Endpoints mirror {@link EditorSettingsResource}:
 * <ul>
 * <li>{@code /ai-form-instructions/global} — the user global (USER -&gt; SYSTEM).
 * No tenant UUID in the path, so ownership is the caller's own user id via
 * {@link CurrentUser}.</li>
 * <li>{@code /projects/{id}/ai-form-instructions} — project override
 * (PROJECT -&gt; USER -&gt; SYSTEM).</li>
 * <li>{@code /books/{id}/ai-form-instructions} — book override
 * (BOOK -&gt; PROJECT -&gt; USER -&gt; SYSTEM).</li>
 * </ul>
 * The {@code projects/{id}} and {@code books/{id}} segments are authorized by the
 * tenant filter, exactly as for project styles and editor settings.
 *
 * <p>
 * Each GET returns the value to pre-populate the dialog plus {@code scope}
 * (where that value came from) and {@code hasOwnOverride} (whether this exact
 * scope holds its own override, so the UI can enable a "remove override"
 * action). PUT sets an override at the addressed scope and requires non-blank
 * text — clearing an override is done with DELETE, which returns the value the
 * scope now falls back to.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiFormInstructionsResource {

    private final AiFormInstructionsDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public AiFormInstructionsResource(AiFormInstructionsDao dao) {
        this.dao = dao;
    }

    /** Request body for PUT. */
    public static class InstructionsRequest {
        @JsonProperty
        public String instructions;
    }

    /** Response view returned by every endpoint. */
    public static class View {
        @JsonProperty
        public String  scope;
        @JsonProperty
        public String  instructions;
        @JsonProperty
        public boolean hasOwnOverride;

        View(Resolved r) {
            this.scope = r.scope();
            this.instructions = r.instructions();
            this.hasOwnOverride = r.hasOwnOverride();
        }
    }

    private UUID u() {
        return CurrentUser.id(request);
    }

    // ── Global (user) ─────────────────────────────────────────────────────────

    @GET
    @Path("/ai-form-instructions/global")
    public Response getGlobal() {
        return run(() -> Response.ok(new View(dao.resolveGlobal(u()))).build());
    }

    @PUT
    @Path("/ai-form-instructions/global")
    public Response putGlobal(InstructionsRequest body) {
        if (isBlank(body))
            return blankError();
        return run(() -> {
            dao.upsertGlobal(u(), body.instructions.trim());
            return Response.ok(new View(dao.resolveGlobal(u()))).build();
        });
    }

    @DELETE
    @Path("/ai-form-instructions/global")
    public Response deleteGlobal() {
        return run(() -> {
            dao.deleteGlobal(u());
            return Response.ok(new View(dao.resolveGlobal(u()))).build();
        });
    }

    // ── Project override ──────────────────────────────────────────────────────

    @GET
    @Path("/projects/{id}/ai-form-instructions")
    public Response getProject(@PathParam("id") UUID projectId) {
        return run(() -> Response.ok(new View(dao.resolveForProject(u(), projectId))).build());
    }

    @PUT
    @Path("/projects/{id}/ai-form-instructions")
    public Response putProject(@PathParam("id") UUID projectId, InstructionsRequest body) {
        if (isBlank(body))
            return blankError();
        return run(() -> {
            dao.setProject(projectId, body.instructions.trim());
            return Response.ok(new View(dao.resolveForProject(u(), projectId))).build();
        });
    }

    @DELETE
    @Path("/projects/{id}/ai-form-instructions")
    public Response deleteProject(@PathParam("id") UUID projectId) {
        return run(() -> {
            dao.clearProject(projectId);
            return Response.ok(new View(dao.resolveForProject(u(), projectId))).build();
        });
    }

    // ── Book override ─────────────────────────────────────────────────────────

    @GET
    @Path("/books/{id}/ai-form-instructions")
    public Response getBook(@PathParam("id") UUID bookId) {
        return run(() -> Response.ok(new View(dao.resolveForBook(u(), bookId))).build());
    }

    @PUT
    @Path("/books/{id}/ai-form-instructions")
    public Response putBook(@PathParam("id") UUID bookId, InstructionsRequest body) {
        if (isBlank(body))
            return blankError();
        return run(() -> {
            dao.setBook(bookId, body.instructions.trim());
            return Response.ok(new View(dao.resolveForBook(u(), bookId))).build();
        });
    }

    @DELETE
    @Path("/books/{id}/ai-form-instructions")
    public Response deleteBook(@PathParam("id") UUID bookId) {
        return run(() -> {
            dao.clearBook(bookId);
            return Response.ok(new View(dao.resolveForBook(u(), bookId))).build();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isBlank(InstructionsRequest body) {
        return body == null || body.instructions == null || body.instructions.isBlank();
    }

    private static Response blankError() {
        return Response.status(Status.BAD_REQUEST)
                .entity(java.util.Map.of("error", "bad_request",
                        "message", "instructions must not be blank; use DELETE to remove an override."))
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
