package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.ChapterMemory;
import com.richardsand.novelkms.service.AiReviewService;

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

/**
 * Chapter memory-document endpoints.
 *
 * <p>Authorization: every path carries a {@code chapters/{id}} or
 * {@code books/{id}} segment, so the tenant filter enforces ownership before the
 * resource runs — exactly like {@link AiReviewResource}'s run endpoints.
 * <ul>
 *   <li>{@code POST   /ai/memory/chapters/{chapterId}} — (re)generate via the provider.</li>
 *   <li>{@code GET    /ai/memory/chapters/{chapterId}} — fetch the current document (or 404).</li>
 *   <li>{@code PUT    /ai/memory/chapters/{chapterId}} — save an author edit.</li>
 *   <li>{@code DELETE /ai/memory/chapters/{chapterId}} — clear the document.</li>
 *   <li>{@code GET    /books/{bookId}/memory-status} — per-chapter staleness for the
 *       pre-review warning.</li>
 * </ul>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChapterMemoryResource {

    private final AiReviewService service;

    @Context
    ContainerRequestContext request;

    @Inject
    public ChapterMemoryResource(AiReviewService service) {
        this.service = service;
    }

    /** Body for POST: optional explicit credential, model override, and one-time guidance. */
    public static class GenerateRequest {
        @JsonProperty
        public UUID credentialId;
        @JsonProperty
        public String model;
        /** Optional one-time author note for this generation only; null/blank = none. */
        @JsonProperty
        public String userGuidance;
    }

    /** Body for PUT: the edited document text. */
    public static class EditRequest {
        @JsonProperty
        public String content;
    }

    @POST
    @Path("/ai/memory/chapters/{chapterId}")
    public Response generate(@PathParam("chapterId") UUID chapterId, GenerateRequest body) {
        UUID   userId       = CurrentUser.id(request);
        UUID   credentialId = body == null ? null : body.credentialId;
        String model        = body == null ? null : body.model;
        String userGuidance = body == null ? null : body.userGuidance;
        try {
            ChapterMemory memory = service.generateChapterMemory(userId, chapterId, credentialId, model, userGuidance);
            return Response.ok(memory).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/memory/chapters/{chapterId}")
    public Response get(@PathParam("chapterId") UUID chapterId) {
        try {
            return service.getChapterMemory(chapterId)
                    .map(memory -> Response.ok(memory).build())
                    .orElseGet(() -> notFound());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PUT
    @Path("/ai/memory/chapters/{chapterId}")
    public Response edit(@PathParam("chapterId") UUID chapterId, EditRequest body) {
        if (body == null || body.content == null || body.content.isBlank()) {
            return error(400, "bad_request", "content must not be blank; use DELETE to clear the document.");
        }
        try {
            ChapterMemory memory = service.editChapterMemory(chapterId, body.content.strip());
            return Response.ok(memory).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/ai/memory/chapters/{chapterId}")
    public Response clear(@PathParam("chapterId") UUID chapterId) {
        try {
            return service.deleteChapterMemory(chapterId)
                    ? Response.noContent().build()
                    : notFound();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/books/{bookId}/memory-status")
    public Response bookStatus(@PathParam("bookId") UUID bookId) {
        try {
            return Response.ok(service.bookMemoryStatus(bookId)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Response notFound() {
        return Response.status(404).entity(Map.of("error", "not_found")).build();
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("error", code, "message", message)).build();
    }

    private static Response serverError() {
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
