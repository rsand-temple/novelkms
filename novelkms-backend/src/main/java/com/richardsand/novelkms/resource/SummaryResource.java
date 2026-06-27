package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.BookSummary;
import com.richardsand.novelkms.model.ChapterSummary;
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
import jakarta.ws.rs.core.Response.Status;

/**
 * Chapter-summary and book-summary endpoints — a separate AI artifact family
 * from memory documents ({@link ChapterMemoryResource}).
 *
 * <p>Authorization: every path carries a {@code chapters/{id}} or
 * {@code books/{id}} segment, so the tenant filter enforces ownership before the
 * resource runs.
 * <ul>
 *   <li>{@code POST   /ai/summary/chapters/{chapterId}} — (re)generate a chapter summary.</li>
 *   <li>{@code GET    /ai/summary/chapters/{chapterId}} — fetch it (or 400).</li>
 *   <li>{@code PUT    /ai/summary/chapters/{chapterId}} — save an author edit.</li>
 *   <li>{@code DELETE /ai/summary/chapters/{chapterId}} — clear it.</li>
 *   <li>{@code GET    /books/{bookId}/chapter-summaries} — aggregated, read-only,
 *       in book order, with per-chapter staleness (the "view chapter summary" view
 *       and the pre-book-summary coverage warning).</li>
 *   <li>{@code POST   /ai/summary/books/{bookId}} — (re)generate the book summary
 *       from the chapter summaries.</li>
 *   <li>{@code GET    /ai/summary/books/{bookId}} — fetch it (or 400).</li>
 *   <li>{@code PUT    /ai/summary/books/{bookId}} — save an author edit.</li>
 *   <li>{@code DELETE /ai/summary/books/{bookId}} — clear it.</li>
 *   <li>{@code GET    /books/{bookId}/book-summary-status} — book-summary card
 *       status + chapter-summary coverage.</li>
 * </ul>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SummaryResource {

    private final AiReviewService service;

    @Context
    ContainerRequestContext request;

    @Inject
    public SummaryResource(AiReviewService service) {
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

    /** Body for PUT: the edited summary text. */
    public static class EditRequest {
        @JsonProperty
        public String content;
    }

    // ── Chapter summary ───────────────────────────────────────────────────────

    @POST
    @Path("/ai/summary/chapters/{chapterId}")
    public Response generateChapter(@PathParam("chapterId") UUID chapterId, GenerateRequest body) {
        UUID   userId       = CurrentUser.id(request);
        UUID   credentialId = body == null ? null : body.credentialId;
        String model        = body == null ? null : body.model;
        String userGuidance = body == null ? null : body.userGuidance;
        try {
            ChapterSummary summary = service.generateChapterSummary(userId, chapterId, credentialId, model, userGuidance);
            return Response.ok(summary).build();
        } catch (ReviewException re) {
            return error(re.status(), re.code(), re.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/summary/chapters/{chapterId}")
    public Response getChapter(@PathParam("chapterId") UUID chapterId) {
        try {
            return service.getChapterSummary(chapterId)
                    .map(summary -> Response.ok(summary).build())
                    .orElseGet(() -> Response.noContent().build());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PUT
    @Path("/ai/summary/chapters/{chapterId}")
    public Response editChapter(@PathParam("chapterId") UUID chapterId, EditRequest body) {
        if (body == null || body.content == null || body.content.isBlank()) {
            return error(Status.BAD_REQUEST, "bad_request", "content must not be blank; use DELETE to clear the summary.");
        }
        try {
            ChapterSummary summary = service.editChapterSummary(chapterId, body.content.strip());
            return Response.ok(summary).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/ai/summary/chapters/{chapterId}")
    public Response clearChapter(@PathParam("chapterId") UUID chapterId) {
        try {
            return service.deleteChapterSummary(chapterId)
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/books/{bookId}/chapter-summaries")
    public Response bookChapterSummaries(@PathParam("bookId") UUID bookId) {
        try {
            return Response.ok(service.bookChapterSummaries(bookId)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    // ── Book summary ──────────────────────────────────────────────────────────

    @POST
    @Path("/ai/summary/books/{bookId}")
    public Response generateBook(@PathParam("bookId") UUID bookId, GenerateRequest body) {
        UUID   userId       = CurrentUser.id(request);
        UUID   credentialId = body == null ? null : body.credentialId;
        String model        = body == null ? null : body.model;
        String userGuidance = body == null ? null : body.userGuidance;
        try {
            BookSummary summary = service.generateBookSummary(userId, bookId, credentialId, model, userGuidance);
            return Response.ok(summary).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/ai/summary/books/{bookId}")
    public Response getBook(@PathParam("bookId") UUID bookId) {
        try {
            return service.getBookSummary(bookId)
                    .map(summary -> Response.ok(summary).build())
                    .orElseGet(() -> Response.noContent().build());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PUT
    @Path("/ai/summary/books/{bookId}")
    public Response editBook(@PathParam("bookId") UUID bookId, EditRequest body) {
        if (body == null || body.content == null || body.content.isBlank()) {
            return error(Status.BAD_REQUEST, "bad_request", "content must not be blank; use DELETE to clear the summary.");
        }
        try {
            BookSummary summary = service.editBookSummary(bookId, body.content.strip());
            return Response.ok(summary).build();
        } catch (ReviewException e) {
            return error(e.status(), e.code(), e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @DELETE
    @Path("/ai/summary/books/{bookId}")
    public Response clearBook(@PathParam("bookId") UUID bookId) {
        try {
            return service.deleteBookSummary(bookId)
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GET
    @Path("/books/{bookId}/book-summary-status")
    public Response bookStatus(@PathParam("bookId") UUID bookId) {
        try {
            return Response.ok(service.bookSummaryStatus(bookId)).build();
        } catch (SQLException e) {
            return serverError();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    private static Response error(Status status, String code, String message) {
        return Response.status(status).entity(Map.of("error", code, "message", message)).build();
    }

    private static Response serverError() {
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
