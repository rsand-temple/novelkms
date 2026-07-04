package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.user.UserPreferenceDao;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
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
 * Per-user UI preferences ({@code user_preference}).
 *
 * <p>A flat key/value bag — the home for small per-user toggles such as
 * {@code skipDeleteConfirm}. The {@code /preferences} path carries no tenant
 * UUID, so (like {@code /editor-settings/global} and the AI credential
 * endpoints) ownership is enforced here via {@link CurrentUser} and the DAO is
 * {@code user_id}-scoped on every query.
 *
 * <ul>
 *   <li>{@code GET    /preferences}        — all of the caller's preferences as a map</li>
 *   <li>{@code PUT    /preferences/{key}}  — upsert one preference ({@code { "value": ... }})</li>
 *   <li>{@code DELETE /preferences/{key}}  — remove one preference</li>
 * </ul>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserPreferenceResource {

    private final UserPreferenceDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public UserPreferenceResource(UserPreferenceDao dao) {
        this.dao = dao;
    }

    public static class ValueRequest {
        @JsonProperty
        public String value;
    }

    private UUID u() {
        return CurrentUser.id(request);
    }

    private static String key(String raw) {
        String k = raw == null ? "" : raw.trim();
        if (k.isEmpty() || k.length() > 200)
            throw new BadRequestException("invalid preference key");
        return k;
    }

    @GET
    @Path("/preferences")
    public Response getAll() {
        return run(() -> Response.ok(dao.getAll(u())).build());
    }

    @PUT
    @Path("/preferences/{key}")
    public Response put(@PathParam("key") String rawKey, ValueRequest r) {
        String k = key(rawKey);
        String value = r == null ? null : r.value;
        return run(() -> {
            dao.upsert(u(), k, value);
            return Response.ok(Map.of("key", k, "value", value == null ? "" : value)).build();
        });
    }

    @DELETE
    @Path("/preferences/{key}")
    public Response delete(@PathParam("key") String rawKey) {
        String k = key(rawKey);
        return run(() -> dao.delete(u(), k)
                ? Response.ok().build()
                : Response.noContent().build());
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
