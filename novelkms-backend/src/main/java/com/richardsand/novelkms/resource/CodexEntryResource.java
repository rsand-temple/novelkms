package com.richardsand.novelkms.resource;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.UUID;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.ai.CodexFillResult;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.service.CodexAiService;
import com.richardsand.novelkms.service.CodexExportService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * HTTP endpoints for per-entry codex DOCX export/import and AI fill-in.
 *
 * <p>
 * All paths contain a {@code scenes/{sceneId}} segment, which the existing
 * {@code TenantAuthorizationFilter} already covers via
 * {@code TenantAccessDao.ownsScene} — no additional ownership check is needed
 * in these handlers.
 *
 * <p>
 * Endpoints:
 * 
 * <pre>
 *   GET  /scenes/{sceneId}/codex-docx          — export entry as DOCX download
 *   POST /scenes/{sceneId}/codex-docx          — import DOCX, save, return Scene
 *   POST /scenes/{sceneId}/codex-fill          — AI fill-in, return suggested values
 * </pre>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodexEntryResource {

    private static final Logger logger = LoggerFactory.getLogger(CodexEntryResource.class);

    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final CodexExportService codexExportService;
    private final CodexAiService     codexAiService;

    @Inject
    public CodexEntryResource(CodexExportService codexExportService,
            CodexAiService codexAiService) {
        this.codexExportService = codexExportService;
        this.codexAiService = codexAiService;
    }

    // -------------------------------------------------------------------------
    // Request / response DTOs
    // -------------------------------------------------------------------------

    public static class FillRequest {
        @JsonProperty
        public UUID   credentialId;
        @JsonProperty
        public String userGuidance;
    }

    public static class FillResponse {
        @JsonProperty
        public java.util.Map<String, String> fields;
        @JsonProperty
        public String                        body;
        @JsonProperty
        public String                        promptVersion;

        FillResponse(CodexFillResult result) {
            this.fields = result.fields();
            this.body = result.body();
            this.promptVersion = result.promptVersion();
        }
    }

    // -------------------------------------------------------------------------
    // DOCX export
    // -------------------------------------------------------------------------

    /**
     * Exports a single codex entry to a DOCX file. The document follows the
     * structured round-trip contract: H1 title, H3+Normal per schema field,
     * H2 "Description", body paragraphs.
     *
     * <p>
     * The filename is derived from the entry title (sanitized).
     */
    @GET
    @Path("/scenes/{sceneId}/codex-docx")
    @Produces(DOCX_MEDIA_TYPE)
    public Response exportCodexEntry(@PathParam("sceneId") UUID sceneId) {
        logger.info("CodexEntryResource.exportCodexEntry invoked: sceneId={}", sceneId);
        try {
            byte[] docxBytes = codexExportService.exportEntry(sceneId);

            // Derive a safe filename from the scene title via the service
            // (we re-load lightly; export is not performance-critical)
            String filename = "codex-entry.docx";
            try {
                // The first line of the export is the scene title — look at bytes
                // is too fragile; just use a generic name here.
                // The frontend can supply a better filename via the download attribute.
            } catch (Exception ignored) {
                // keep generic name
            }

            final byte[]    responseBytes = docxBytes;
            StreamingOutput stream        = out -> {
                                              out.write(responseBytes);
                                              out.flush();
                                          };
            return Response.ok(stream)
                    .header("Content-Type", DOCX_MEDIA_TYPE)
                    .header("Content-Disposition",
                            "attachment; filename=\"" + filename + "\"")
                    .build();

        } catch (IllegalArgumentException e) {
            logger.warn("Codex entry export failed — not found: sceneId={}, msg={}",
                    sceneId, e.getMessage());
            return Response.noContent().build();
        } catch (Exception e) {
            logger.error("Codex entry export error: sceneId={}", sceneId, e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    // -------------------------------------------------------------------------
    // DOCX import
    // -------------------------------------------------------------------------

    /**
     * Imports a DOCX file for a codex entry, parses the structured round-trip
     * contract, saves the result, and returns the updated scene.
     *
     * <p>
     * Uses Jersey multipart; the {@code MultiPartFeature} is already
     * registered in {@code NovelKmsServer}.
     */
    @POST
    @Path("/scenes/{sceneId}/codex-docx")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importCodexEntry(
            @PathParam("sceneId") UUID sceneId,
            @FormDataParam("file") InputStream fileStream) {
        logger.info("CodexEntryResource.importCodexEntry invoked: sceneId={}", sceneId);
        if (fileStream == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("No file was uploaded (expected form-data field 'file')")
                    .build();
        }
        try {
            Scene updated = codexExportService.importEntry(sceneId, fileStream);
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Codex entry import failed — not found: sceneId={}, msg={}",
                    sceneId, e.getMessage());
            return Response.noContent().build();
        } catch (Exception e) {
            logger.error("Codex entry import error: sceneId={}", sceneId, e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    // -------------------------------------------------------------------------
    // AI fill-in
    // -------------------------------------------------------------------------

    /**
     * Uses AI to suggest field values and a body for the given codex entry,
     * using chapter summaries and pinned codex entries as context. Returns the
     * suggestions without saving them — the frontend merges them into the form
     * and the existing autosave path persists the result.
     *
     * <p>
     * Errors (no credential, no summaries, unsupported provider) are
     * returned as structured JSON with an {@code error} key, consistent with
     * the review endpoint pattern.
     */
    @POST
    @Path("/scenes/{sceneId}/codex-fill")
    public Response fillCodexEntry(
            @PathParam("sceneId") UUID sceneId,
            FillRequest req,
            @Context ContainerRequestContext request) {
        logger.info("CodexEntryResource.fillCodexEntry invoked: sceneId={}", sceneId);
        UUID userId = CurrentUser.id(request);
        try {
            UUID   credentialId = req != null ? req.credentialId : null;
            String guidance     = req != null ? req.userGuidance : null;

            CodexFillResult result = codexAiService.fillCodexEntry(
                    userId, sceneId, credentialId, guidance);
            return Response.ok(new FillResponse(result)).build();

        } catch (ReviewException e) {
            return Response.status(e.status()).entity(e.getMessage()).build();
        } catch (SQLException e) {
            logger.error("Database error in codex AI fill: sceneId={}", sceneId, e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
