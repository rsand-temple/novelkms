package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.ChapterEditorial;
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
 * Chapter-editorial endpoints — a separate, author-facing AI artifact family
 * from memory documents ({@link ChapterMemoryResource}) and summaries
 * ({@link SummaryResource}). An editorial is a short editorial reading of one
 * chapter; since V36 there is one per (chapter, provider). Unlike a memory
 * document, an editorial is never consumed by any other AI function.
 *
 * <p>Backward-compatible like the other families: the single-editorial
 * {@code GET} returns the preferred editorial (default provider's variant, else
 * most-recently-updated), an explicit {@code ?provider=} returns that variant,
 * and a {@code /variants} endpoint enumerates all provider variants. Edit/delete
 * accept an optional provider defaulting to the user's default provider.
 *
 * <p>Authorization: every path carries a {@code chapters/{id}} segment, so the
 * tenant filter enforces ownership before the resource runs.
 * <ul>
 *   <li>{@code POST   /ai/editorial/chapters/{chapterId}} — (re)generate.</li>
 *   <li>{@code GET    /ai/editorial/chapters/{chapterId}} — preferred (or {@code ?provider=}) editorial; 204 if none.</li>
 *   <li>{@code GET    /ai/editorial/chapters/{chapterId}/variants} — every provider variant.</li>
 *   <li>{@code PUT    /ai/editorial/chapters/{chapterId}} — save an author edit (per provider).</li>
 *   <li>{@code DELETE /ai/editorial/chapters/{chapterId}} — clear it (per provider).</li>
 * </ul>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EditorialResource {

    private final AiReviewService service;

    @Context
    ContainerRequestContext request;

    @Inject
    public EditorialResource(AiReviewService service) {
        this.service = service;
    }

    /** Body for POST: optional explicit credential, model override, one-time guidance, and pinned-context opt-out. */
    public static class GenerateRequest {
        @JsonProperty
        public UUID credentialId;
        @JsonProperty
        public String model;
        /** Optional one-time author note for this generation only; null/blank = none. */
        @JsonProperty
        public String userGuidance;
        /** Include pinned Codex reference entries in the prompt; defaults to true when null. */
        @JsonProperty
        public Boolean includePinnedContext;
    }

    /** Body for PUT: the edited editorial text and the provider variant it belongs to. */
    public static class EditRequest {
        @JsonProperty
        public String content;
        /** Provider variant being edited; null/blank falls back to the user's default provider. */
        @JsonProperty
        public String provider;
    }

    @POST
    @Path("/ai/editorial/chapters/{chapterId}")
    public Response generate(@PathParam("chapterId") UUID chapterId, GenerateRequest body) {
        UUID    userId        = CurrentUser.id(request);
        UUID    credentialId  = body == null ? null : body.credentialId;
        String  model         = body == null ? null : body.model;
        String  userGuidance  = body == null ? null : body.userGuidance;
        boolean includePinned = body == null || body.includePinnedContext == null || body.includePinnedContext;
        try {
            ChapterEditorial editorial = service.generateChapterEditorial(
                    userId, chapterId, credentialId, model, userGuidance, includePinned);
            return Response.ok(editorial).build();
        } catch (ReviewException re) {
            return error(re.status(), re.code(), re.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/editorial/chapters/{chapterId}")
    public Response get(@PathParam("chapterId") UUID chapterId,
            @QueryParam("provider") String provider) {
        UUID userId = CurrentUser.id(request);
        try {
            Optional<ChapterEditorial> editorial = (provider != null && !provider.isBlank())
                    ? service.getChapterEditorial(chapterId, provider)
                    : service.getPreferredChapterEditorial(chapterId, service.defaultProviderKey(userId));
            return editorial
                    .map(ed -> Response.ok(ed).build())
                    .orElseGet(() -> Response.noContent().build());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/editorial/chapters/{chapterId}/variants")
    public Response variants(@PathParam("chapterId") UUID chapterId) {
        try {
            return Response.ok(service.listChapterEditorialVariants(chapterId)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PUT
    @Path("/ai/editorial/chapters/{chapterId}")
    public Response edit(@PathParam("chapterId") UUID chapterId, EditRequest body) {
        if (body == null || body.content == null || body.content.isBlank()) {
            return error(Status.BAD_REQUEST, "bad_request", "content must not be blank; use DELETE to clear the editorial.");
        }
        UUID userId = CurrentUser.id(request);
        try {
            String provider = resolveProvider(body.provider, userId);
            ChapterEditorial editorial = service.editChapterEditorial(chapterId, provider, body.content.strip());
            return Response.ok(editorial).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/ai/editorial/chapters/{chapterId}")
    public Response clear(@PathParam("chapterId") UUID chapterId,
            @QueryParam("provider") String provider) {
        UUID userId = CurrentUser.id(request);
        try {
            return service.deleteChapterEditorial(chapterId, resolveProvider(provider, userId))
                    ? Response.ok().build()
                    : Response.noContent().build();
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
