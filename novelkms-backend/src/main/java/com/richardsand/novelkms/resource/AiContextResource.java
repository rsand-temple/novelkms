package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.CodexDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.service.AiReviewService;
import com.richardsand.novelkms.service.AiReviewService.PinnedContextSummary;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * "Share Codex entries with the AI" endpoints. The author selectively pins
 * individual Codex entries to have them fed into chapter/scene review prompts as
 * reference context (established canon/voice the manuscript must respect),
 * without dumping the whole Codex at the model. Nothing is shared by default.
 *
 * <p>
 * Authorization is path-segment based: the tenant filter checks ownership of the
 * {@code scenes/{id}}, {@code chapters/{id}}, {@code codex/{id}}, and
 * {@code books/{id}} segments, so no in-resource ownership check is needed.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiContextResource {

    private static final Logger logger = LoggerFactory.getLogger(AiContextResource.class);

    private final SceneDao        sceneDao;
    private final ChapterDao      chapterDao;
    private final CodexDao        codexDao;
    private final AiReviewService aiReviewService;

    @Inject
    public AiContextResource(SceneDao sceneDao, ChapterDao chapterDao, CodexDao codexDao,
            AiReviewService aiReviewService) {
        this.sceneDao = sceneDao;
        this.chapterDao = chapterDao;
        this.codexDao = codexDao;
        this.aiReviewService = aiReviewService;
    }

    // -------------------------------------------------------------------------
    // Request / response DTOs
    // -------------------------------------------------------------------------

    public static class PinRequest {
        @JsonProperty
        public boolean pinned;
    }

    /** One Codex entry as the Manage AI Context dialog renders it (no content). */
    public static class ContextEntry {
        @JsonProperty public UUID    sceneId;
        @JsonProperty public UUID    chapterId;
        @JsonProperty public String  category;
        @JsonProperty public String  categoryTitle;
        @JsonProperty public String  title;
        @JsonProperty public int     wordCount;
        @JsonProperty public boolean pinned;

        ContextEntry(CodexDao.AiContextEntry e) {
            this.sceneId       = e.sceneId();
            this.chapterId     = e.chapterId();
            this.category      = e.categoryKey();
            this.categoryTitle = e.categoryTitle();
            this.title         = e.title();
            this.wordCount     = e.wordCount();
            this.pinned        = e.pinned();
        }
    }

    // -------------------------------------------------------------------------
    // Single-entry toggle
    // -------------------------------------------------------------------------

    @PUT
    @Path("/scenes/{id}/ai-context-pin")
    public Response setScenePinned(@PathParam("id") UUID id, PinRequest body) {
        boolean pinned = body != null && body.pinned;
        logger.info("AiContextResource.setScenePinned invoked: sceneId={}, pinned={}", id, pinned);
        try {
            return sceneDao.setAiContextPinned(id, pinned)
                    .map(s -> Response.ok(s).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e, "set scene AI-context pin");
        }
    }

    // -------------------------------------------------------------------------
    // Bulk toggle for a whole Codex category
    // -------------------------------------------------------------------------

    @PUT
    @Path("/chapters/{chapterId}/ai-context-pin")
    public Response setCategoryPinned(@PathParam("chapterId") UUID chapterId, PinRequest body) {
        boolean pinned = body != null && body.pinned;
        logger.info("AiContextResource.setCategoryPinned invoked: chapterId={}, pinned={}", chapterId, pinned);
        try {
            Optional<Chapter> chapter = chapterDao.findById(chapterId);
            if (chapter.isEmpty()) {
                return Response.noContent().build();
            }
            if (chapter.get().getCodexId() == null) {
                // Manuscript chapter — pinning is only meaningful for codex categories.
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "not_codex_category",
                                "message", "AI context is only available for Codex categories."))
                        .build();
            }
            int updated = sceneDao.setAiContextPinnedForChapter(chapterId, pinned);
            return Response.ok(Map.of("updated", updated)).build();
        } catch (SQLException e) {
            return serverError(e, "set category AI-context pin");
        }
    }

    // -------------------------------------------------------------------------
    // Aggregate read for the Manage AI Context dialog (one codex)
    // -------------------------------------------------------------------------

    @GET
    @Path("/codex/{codexId}/ai-context")
    public Response listForCodex(@PathParam("codexId") UUID codexId) {
        logger.debug("AiContextResource.listForCodex invoked: codexId={}", codexId);
        try {
            List<CodexDao.AiContextEntry> rows    = codexDao.listAiContextEntries(codexId);
            List<ContextEntry>            entries = new ArrayList<>(rows.size());
            int pinnedCount = 0;
            int pinnedWords = 0;
            for (CodexDao.AiContextEntry e : rows) {
                entries.add(new ContextEntry(e));
                if (e.pinned()) {
                    pinnedCount++;
                    pinnedWords += e.wordCount();
                }
            }
            return Response.ok(Map.of(
                    "pinnedCount", pinnedCount,
                    "pinnedWords", pinnedWords,
                    "entries", entries)).build();
        } catch (SQLException e) {
            return serverError(e, "list codex AI context");
        }
    }

    // -------------------------------------------------------------------------
    // Per-book pinned-context summary for the review rail
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/ai-context-summary")
    public Response bookSummary(@PathParam("bookId") UUID bookId) {
        logger.debug("AiContextResource.bookSummary invoked: bookId={}", bookId);
        try {
            PinnedContextSummary s = aiReviewService.pinnedContextSummary(bookId);
            return Response.ok(Map.of(
                    "entryCount", s.entryCount(),
                    "wordCount", s.wordCount())).build();
        } catch (SQLException e) {
            return serverError(e, "summarize book AI context");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Response serverError(SQLException e, String action) {
        logger.error("Database error during {}: {}", action, e.getMessage(), e);
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
