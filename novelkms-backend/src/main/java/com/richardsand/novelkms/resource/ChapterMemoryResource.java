package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Chapter memory-document endpoints.
 *
 * <p>Since V36 a chapter holds one memory document per provider. To stay
 * backward-compatible with the current frontend while the provider-selector UI
 * is built (Phase 2), the single-document {@code GET} keeps returning ONE
 * document: an explicit {@code ?provider=} yields exactly that provider's
 * document, and its absence yields the user's <em>preferred</em> document (the
 * default provider's variant, else the most-recently-updated variant of any
 * provider). Edit/delete/status likewise accept an optional provider and default
 * to the user's default provider. A new {@code /variants} endpoint returns every
 * provider variant for the selector to enumerate.
 *
 * <p>Authorization: every path carries a {@code chapters/{id}} or
 * {@code books/{id}} segment, so the tenant filter enforces ownership before the
 * resource runs — exactly like {@link AiReviewResource}'s run endpoints.
 * <ul>
 *   <li>{@code POST   /ai/memory/chapters/{chapterId}} — (re)generate via the provider.</li>
 *   <li>{@code GET    /ai/memory/chapters/{chapterId}} — fetch the preferred document
 *       (or the {@code ?provider=} variant); 204 if none.</li>
 *   <li>{@code GET    /ai/memory/chapters/{chapterId}/variants} — every provider variant.</li>
 *   <li>{@code PUT    /ai/memory/chapters/{chapterId}} — save an author edit (per provider).</li>
 *   <li>{@code DELETE /ai/memory/chapters/{chapterId}} — clear the document (per provider).</li>
 *   <li>{@code GET    /books/{bookId}/memory-status} — per-chapter staleness for the
 *       pre-review warning, for the given (or default) provider.</li>
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

    /** Body for PUT: the edited document text and the provider variant it belongs to. */
    public static class EditRequest {
        @JsonProperty
        public String content;
        /** Provider variant being edited; null/blank falls back to the user's default provider. */
        @JsonProperty
        public String provider;
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
        } catch (ReviewException re) {
            return error(re.status(), re.code(), re.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/memory/chapters/{chapterId}")
    public Response get(@PathParam("chapterId") UUID chapterId,
            @QueryParam("provider") String provider) {
        UUID userId = CurrentUser.id(request);
        try {
            Optional<ChapterMemory> memory = (provider != null && !provider.isBlank())
                    ? service.getChapterMemory(chapterId, provider)
                    : service.getPreferredChapterMemory(chapterId, service.defaultProviderKey(userId));
            return memory
                    .map(m -> Response.ok(m).build())
                    .orElseGet(() -> Response.noContent().build());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/memory/chapters/{chapterId}/variants")
    public Response variants(@PathParam("chapterId") UUID chapterId) {
        try {
            return Response.ok(service.listChapterMemoryVariants(chapterId)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PUT
    @Path("/ai/memory/chapters/{chapterId}")
    public Response edit(@PathParam("chapterId") UUID chapterId, EditRequest body) {
        if (body == null || body.content == null || body.content.isBlank()) {
            return error(Status.BAD_REQUEST, "bad_request", "content must not be blank; use DELETE to clear the document.");
        }
        UUID userId = CurrentUser.id(request);
        try {
            String provider = resolveProvider(body.provider, userId);
            ChapterMemory memory = service.editChapterMemory(chapterId, provider, body.content.strip());
            return Response.ok(memory).build();
        } catch (ReviewException re) {
            return error(re.status(), re.code(), re.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/ai/memory/chapters/{chapterId}")
    public Response clear(@PathParam("chapterId") UUID chapterId,
            @QueryParam("provider") String provider) {
        UUID userId = CurrentUser.id(request);
        try {
            return service.deleteChapterMemory(chapterId, resolveProvider(provider, userId))
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/books/{bookId}/memory-status")
    public Response bookStatus(@PathParam("bookId") UUID bookId,
            @QueryParam("provider") String provider) {
        UUID userId = CurrentUser.id(request);
        try {
            return Response.ok(service.bookMemoryStatus(bookId, resolveProvider(provider, userId))).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Explicit provider when supplied, otherwise the user's default provider. */
    private String resolveProvider(String provider, UUID userId) throws SQLException {
        return (provider != null && !provider.isBlank()) ? provider : service.defaultProviderKey(userId);
    }

    private static Response error(Status status, String code, String message) {
        return Response.status(status).entity(Map.of("error", code, "message", message)).build();
    }

    private static Response serverError() {
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
