package com.richardsand.novelkms.resource.codex;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
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
 *   GET  /scenes/{sceneId}/codex-chapters     — chapter list with summary status for fill dialog
 *   GET  /scenes/{sceneId}/codex-docx         — export entry as DOCX download
 *   POST /scenes/{sceneId}/codex-docx         — import DOCX, save, return Scene
 *   POST /scenes/{sceneId}/codex-fill         — AI fill-in, return suggested values
 * </pre>
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodexEntryResource {

	private static final Logger logger = LoggerFactory.getLogger(CodexEntryResource.class);

	private static final String DOCX_MEDIA_TYPE =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
		/** Specific AI credential UUID, or null to use the user's default. */
		@JsonProperty
		public UUID credentialId;
		/** Optional one-time author note; not saved persistently. */
		@JsonProperty
		public String userGuidance;
		/**
		 * Chapter IDs whose summaries should be included as manuscript context.
		 * Null or empty means use all available summaries (backward-compatibility).
		 * The fill-dialog always sends this list based on the author's checkbox
		 * selection.
		 */
		@JsonProperty
		public List<UUID> selectedChapterIds;
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
	// Chapter list for fill dialog
	// -------------------------------------------------------------------------

	/**
	 * Returns every manuscript chapter in the codex entry's scope (book or
	 * project), each carrying its summary status, so the fill dialog can show
	 * which chapters have a summary and allow inline generation of missing ones.
	 *
	 * <p>The preferred provider is resolved from the user's default credential;
	 * when no credential is configured the most-recently-updated variant for any
	 * provider is used. Returns an empty array when the scene is not a valid
	 * codex entry — the frontend renders an appropriate empty state.
	 */
	@GET
	@Path("/scenes/{sceneId}/codex-chapters")
	public Response getCodexChapters(
			@PathParam("sceneId") UUID sceneId,
			@Context ContainerRequestContext request) {
		logger.debug("CodexEntryResource.getCodexChapters: sceneId={}", sceneId);
		UUID userId = CurrentUser.id(request);
		try {
			List<CodexAiService.CodexChapterInfo> chapters =
					codexAiService.listChaptersForCodexEntry(userId, sceneId);
			return Response.ok(chapters).build();
		} catch (SQLException e) {
			logger.error("Database error loading codex chapters: sceneId={}", sceneId, e);
			return Response.serverError().entity("Database error").build();
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
	 * <p>The filename is derived from the entry title (sanitized).
	 */
	@GET
	@Path("/scenes/{sceneId}/codex-docx")
	@Produces(DOCX_MEDIA_TYPE)
	public Response exportCodexEntry(@PathParam("sceneId") UUID sceneId) {
		logger.info("CodexEntryResource.exportCodexEntry invoked: sceneId={}", sceneId);
		try {
			byte[] docxBytes = codexExportService.exportEntry(sceneId);

			// Generic filename; the frontend supplies a better one via the download attribute.
			String filename = "codex-entry.docx";

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
	 * <p>Uses Jersey multipart; the {@code MultiPartFeature} is already
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
	 * <p>When {@code selectedChapterIds} is provided in the request body, only
	 * those chapters' summaries are included as manuscript context. This is how
	 * the fill dialog communicates the author's chapter selection.
	 *
	 * <p>Errors (no credential, no summaries for selected chapters, unsupported
	 * provider) are returned as structured JSON with an {@code error} key,
	 * consistent with the review endpoint pattern.
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
			UUID         credentialId       = req != null ? req.credentialId : null;
			String       guidance           = req != null ? req.userGuidance : null;
			List<UUID>   selectedChapterIds = req != null ? req.selectedChapterIds : null;

			CodexFillResult result = codexAiService.fillCodexEntry(
					userId, sceneId, credentialId, guidance, selectedChapterIds);
			return Response.ok(new FillResponse(result)).build();

		} catch (ReviewException e) {
			return Response.status(e.status()).entity(e.getMessage()).build();
		} catch (SQLException e) {
			logger.error("Database error in codex AI fill: sceneId={}", sceneId, e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}
}
