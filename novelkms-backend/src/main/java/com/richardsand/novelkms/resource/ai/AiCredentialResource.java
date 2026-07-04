package com.richardsand.novelkms.resource.ai;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.AiProviderRegistry;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AiCredentialDao;

import jakarta.inject.Inject;
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
import jakarta.ws.rs.core.Response.Status;

/**
 * Per-user AI provider credentials. The API key is write-only: it is accepted on
 * create/update but never returned (responses carry only a masked {@code keyLast4}).
 * Every operation is scoped to the authenticated user inside {@link AiCredentialDao};
 * these paths carry no tenant-entity UUID, so the tenant filter does not apply.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiCredentialResource {

    private static final Logger logger = LoggerFactory.getLogger(AiCredentialResource.class);

    /** Providers a credential may be stored for in v1. */
    private final Set<String> providerKeyNames;

    private final AiCredentialDao dao;

    @Context
    ContainerRequestContext request;

    @Inject
    public AiCredentialResource(AiCredentialDao dao, AiProviderRegistry aiProviderRegistry) {
        this.dao = dao;
        this.providerKeyNames = aiProviderRegistry.getKeys();
    }

    public static class CreateRequest {
        @JsonProperty public String  provider;
        @JsonProperty public String  label;
        @JsonProperty public String  apiKey;
        @JsonProperty public String  defaultModel;
        @JsonProperty public boolean makeDefault;
    }

    public static class UpdateRequest {
        @JsonProperty public String label;
        @JsonProperty public String apiKey;
        @JsonProperty public String defaultModel;
    }

    @GET
    @Path("/ai/credentials")
    public Response list() {
        logger.debug("AiCredentialResource.list invoked");
        return run(() -> Response.ok(dao.findByUser(CurrentUser.id(request))).build());
    }

    @POST
    @Path("/ai/credentials")
    public Response create(CreateRequest body) {
        logger.info("AiCredentialResource.create invoked: provider={}", body == null ? null : body.provider);
        if (body == null || isBlank(body.apiKey)) {
            return error(Status.BAD_REQUEST, "missing_api_key", "An API key is required.");
        }
        String provider = normalize(body.provider, "OPENAI");
        if (!providerKeyNames.contains(provider)) {
            return error(Status.BAD_REQUEST, "unsupported_provider", "Provider " + provider + " is not supported yet.");
        }
        String label = isBlank(body.label) ? "Default" : body.label.trim();
        return run(() -> Response.status(Status.CREATED)
                .entity(dao.create(CurrentUser.id(request), provider, label,
                        body.apiKey.trim(), trimOrNull(body.defaultModel), body.makeDefault))
                .build());
    }

    @PUT
    @Path("/ai/credentials/{id}")
    public Response update(@PathParam("id") UUID id, UpdateRequest body) {
        logger.info("AiCredentialResource.update invoked: id={}", id);
        if (body == null) return error(Status.BAD_REQUEST, "bad_request", "Request body is required.");
        String label = isBlank(body.label) ? "Default" : body.label.trim();
        return run(() -> dao.update(id, CurrentUser.id(request), label,
                        body.apiKey, trimOrNull(body.defaultModel))
                .map(updated -> Response.ok(updated).build())
                .orElseGet(() -> noContent()));
    }

    @DELETE
    @Path("/ai/credentials/{id}")
    public Response delete(@PathParam("id") UUID id) {
        logger.info("AiCredentialResource.delete invoked: id={}", id);
        return run(() -> dao.delete(id, CurrentUser.id(request))
                ? Response.noContent().build() : noContent());
    }

    @POST
    @Path("/ai/credentials/{id}/default")
    public Response makeDefault(@PathParam("id") UUID id) {
        logger.info("AiCredentialResource.makeDefault invoked: id={}", id);
        return run(() -> dao.setDefault(id, CurrentUser.id(request))
                .map(c -> Response.ok(c).build())
                .orElseGet(() -> noContent()));
    }

    private static Response noContent() {
        return Response.status(Status.NO_CONTENT).build();
    }

    private static Response error(Status status, String code, String message) {
        return Response.status(status).entity(Map.of("error", code, "message", message)).build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimOrNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String normalize(String provider, String fallback) {
        return isBlank(provider) ? fallback : provider.trim().toUpperCase();
    }

    private Response run(SqlCall call) {
        try {
            return call.call();
        } catch (SQLException e) {
            logger.error("Database error in AiCredentialResource: {}", e.getMessage(), e);
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    private interface SqlCall {
        Response call() throws SQLException;
    }
}
