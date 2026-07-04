package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.EditorSettingsDao;
import com.richardsand.novelkms.model.editor.EditorSettingsDefinition;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
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

/**
 * Cascading document ("editor") settings.
 *
 * <p>Endpoints mirror {@link StyleResource}:
 * <ul>
 *   <li>{@code /editor-settings/global} — the user default (USER override -> SYSTEM).
 *       Carries no tenant UUID, so ownership is enforced here via {@link CurrentUser}.</li>
 *   <li>{@code /projects/{id}/editor-settings} — the per-project override
 *       (PROJECT -> USER -> SYSTEM). The {@code projects/{id}} segment is
 *       authorized by the tenant filter, exactly as for project styles.</li>
 *   <li>{@code /books/{id}/editor-settings} — the per-book override
 *       (BOOK -> PROJECT -> USER -> SYSTEM); the {@code books/{id}} segment is
 *       likewise tenant-authorized.</li>
 * </ul>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EditorSettingsResource {

    private static final Logger logger = LoggerFactory.getLogger(EditorSettingsResource.class);

    private final EditorSettingsDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public EditorSettingsResource(EditorSettingsDao dao) {
        this.dao = dao;
    }

    public static class DefinitionRequest {
        @JsonProperty
        public EditorSettingsDefinition definition;
    }

    private UUID u() {
        return CurrentUser.id(request);
    }

    private void need(DefinitionRequest r) {
        if (r == null || r.definition == null)
            throw new BadRequestException("definition required");
    }

    // ── User default (all projects) ───────────────────────────────────────────

    @GET
    @Path("/editor-settings/global")
    public Response getUser() {
        logger.debug("EditorSettingsResource.getUser invoked");
        return run(() -> Response.ok(dao.resolveUser(u())).build());
    }

    @PUT
    @Path("/editor-settings/global")
    public Response putUser(DefinitionRequest r) {
        logger.info("EditorSettingsResource.putUser invoked");
        need(r);
        return run(() -> Response.ok(dao.upsertUser(u(), r.definition)).build());
    }

    @POST
    @Path("/editor-settings/global/reset")
    public Response resetUser() {
        logger.info("EditorSettingsResource.resetUser invoked");
        return run(() -> {
            dao.deleteUser(u());
            return Response.ok(dao.resolveUser(u())).build();
        });
    }

    // ── Per-project override ──────────────────────────────────────────────────

    @GET
    @Path("/projects/{id}/editor-settings")
    public Response getProject(@PathParam("id") UUID projectId) {
        logger.debug("EditorSettingsResource.getProject invoked: projectId={}", projectId);
        return run(() -> Response.ok(dao.resolveProject(u(), projectId)).build());
    }

    @PUT
    @Path("/projects/{id}/editor-settings")
    public Response putProject(@PathParam("id") UUID projectId, DefinitionRequest r) {
        logger.info("EditorSettingsResource.putProject invoked: projectId={}", projectId);
        need(r);
        return run(() -> Response.ok(dao.upsertProject(projectId, r.definition)).build());
    }

    @DELETE
    @Path("/projects/{id}/editor-settings")
    public Response deleteProject(@PathParam("id") UUID projectId) {
        logger.info("EditorSettingsResource.deleteProject invoked: projectId={}", projectId);
        return run(() -> dao.deleteProject(projectId)
                ? Response.noContent().build()
                : Response.status(404).build());
    }

    // ── Per-book override ─────────────────────────────────────────────────────

    @GET
    @Path("/books/{id}/editor-settings")
    public Response getBook(@PathParam("id") UUID bookId) {
        logger.debug("EditorSettingsResource.getBook invoked: bookId={}", bookId);
        return run(() -> Response.ok(dao.resolveBook(u(), bookId)).build());
    }

    @PUT
    @Path("/books/{id}/editor-settings")
    public Response putBook(@PathParam("id") UUID bookId, DefinitionRequest r) {
        logger.info("EditorSettingsResource.putBook invoked: bookId={}", bookId);
        need(r);
        return run(() -> Response.ok(dao.upsertBook(bookId, r.definition)).build());
    }

    @DELETE
    @Path("/books/{id}/editor-settings")
    public Response deleteBook(@PathParam("id") UUID bookId) {
        logger.info("EditorSettingsResource.deleteBook invoked: bookId={}", bookId);
        return run(() -> dao.deleteBook(bookId)
                ? Response.ok().build()
                : Response.noContent().build());
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (SQLException e) {
            logger.error("Database error in EditorSettingsResource: {}", e.getMessage(), e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
