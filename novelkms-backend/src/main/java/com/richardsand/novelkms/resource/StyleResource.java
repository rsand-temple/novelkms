package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.UserStyleDao;
import com.richardsand.novelkms.model.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StyleResource {
    private final UserStyleDao dao;
    @Context
    ContainerRequestContext    request;

    @Inject
    public StyleResource(UserStyleDao d) {
        dao = d;
    }

    public static class DefinitionRequest {
        @JsonProperty
        public StyleDefinition definition;
    }

    private UUID u() {
        return CurrentUser.id(request);
    }

    private String k(String x) {
        String z = x == null ? "" : x.trim().toLowerCase();
        if (!StyleDefaults.isValidKey(z))
            throw new BadRequestException("unknown style key");
        return z;
    }

    @GET
    @Path("/styles/global")
    public Response allUser() {
        return run(() -> Response.ok(dao.allUser(u())).build());
    }

    @GET
    @Path("/styles/global/{key}")
    public Response user(@PathParam("key") String k) {
        return run(() -> Response.ok(dao.resolveUser(u(), k(k))).build());
    }

    @PUT
    @Path("/styles/global/{key}")
    public Response putUser(@PathParam("key") String k, DefinitionRequest r) {
        need(r);
        return run(() -> Response.ok(dao.upsertUser(u(), k(k), r.definition)).build());
    }

    @POST
    @Path("/styles/global/{key}/reset")
    public Response resetUser(@PathParam("key") String k) {
        return run(() -> {
            dao.deleteUser(u(), k(k));
            return Response.ok(dao.resolveUser(u(), k(k))).build();
        });
    }

    @GET
    @Path("/projects/{id}/styles")
    public Response allProject(@PathParam("id") UUID p) {
        return run(() -> Response.ok(dao.allProject(u(), p)).build());
    }

    @GET
    @Path("/projects/{id}/styles/{key}")
    public Response project(@PathParam("id") UUID p, @PathParam("key") String k) {
        return run(() -> Response.ok(dao.resolveProject(u(), p, k(k))).build());
    }

    @PUT
    @Path("/projects/{id}/styles/{key}")
    public Response putProject(@PathParam("id") UUID p, @PathParam("key") String k, DefinitionRequest r) {
        need(r);
        return run(() -> Response.ok(dao.upsertProject(p, k(k), r.definition)).build());
    }

    @DELETE
    @Path("/projects/{id}/styles/{key}")
    public Response delProject(@PathParam("id") UUID p, @PathParam("key") String k) {
        return run(() -> dao.deleteProject(p, k(k)) ? Response.noContent().build() : Response.status(404).build());
    }

    @GET
    @Path("/books/{id}/styles")
    public Response allBook(@PathParam("id") UUID b) {
        return run(() -> Response.ok(dao.allBook(u(), b)).build());
    }

    @GET
    @Path("/books/{id}/styles/{key}")
    public Response book(@PathParam("id") UUID b, @PathParam("key") String k) {
        return run(() -> Response.ok(dao.resolveBook(u(), b, k(k))).build());
    }

    @PUT
    @Path("/books/{id}/styles/{key}")
    public Response putBook(@PathParam("id") UUID b, @PathParam("key") String k, DefinitionRequest r) {
        need(r);
        return run(() -> Response.ok(dao.upsertBook(b, k(k), r.definition)).build());
    }

    @DELETE
    @Path("/books/{id}/styles/{key}")
    public Response delBook(@PathParam("id") UUID b, @PathParam("key") String k) {
        return run(() -> dao.deleteBook(b, k(k)) ? Response.noContent().build() : Response.status(404).build());
    }

    private void need(DefinitionRequest r) {
        if (r == null || r.definition == null)
            throw new BadRequestException("definition required");
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (SQLException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
