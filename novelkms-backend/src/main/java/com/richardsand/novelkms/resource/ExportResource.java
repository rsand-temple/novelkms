package com.richardsand.novelkms.resource;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.service.ExportService;
import com.richardsand.novelkms.service.ExportService.ExportMeta;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Download endpoints for exporting manuscript content as Word (.docx) files.
 *
 * Four scopes are supported:
 *   GET /api/export/books/{bookId}/docx      — full book
 *   GET /api/export/parts/{partId}/docx      — one part and its chapters
 *   GET /api/export/chapters/{chapterId}/docx — one chapter and its scenes
 *   GET /api/export/scenes/{sceneId}/docx    — one scene
 *
 * All return application/vnd.openxmlformats-officedocument.wordprocessingml.document
 * with a Content-Disposition: attachment header so the browser triggers a download.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {

    private static final Logger logger = LoggerFactory.getLogger(ExportResource.class);

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExportService exportService;

    @Inject
    public ExportResource(ExportService exportService) {
        this.exportService = exportService;
    }

    // -------------------------------------------------------------------------
    // Book
    // -------------------------------------------------------------------------

    @GET
    @Path("/export/books/{bookId}/docx")
    @Produces(MediaType.WILDCARD)
    public Response exportBook(@PathParam("bookId") UUID bookId) {
        try {
            ExportMeta meta = exportService.exportBook(bookId);
            return download(meta);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Book export failed for bookId={}: {}", bookId, e.getMessage(), e);
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Part
    // -------------------------------------------------------------------------

    @GET
    @Path("/export/parts/{partId}/docx")
    @Produces(MediaType.WILDCARD)
    public Response exportPart(@PathParam("partId") UUID partId) {
        try {
            ExportMeta meta = exportService.exportPart(partId);
            return download(meta);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Part export failed for partId={}: {}", partId, e.getMessage(), e);
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Chapter
    // -------------------------------------------------------------------------

    @GET
    @Path("/export/chapters/{chapterId}/docx")
    @Produces(MediaType.WILDCARD)
    public Response exportChapter(@PathParam("chapterId") UUID chapterId) {
        try {
            ExportMeta meta = exportService.exportChapter(chapterId);
            return download(meta);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Chapter export failed for chapterId={}: {}", chapterId, e.getMessage(), e);
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Scene
    // -------------------------------------------------------------------------

    @GET
    @Path("/export/scenes/{sceneId}/docx")
    @Produces(MediaType.WILDCARD)
    public Response exportScene(@PathParam("sceneId") UUID sceneId) {
        try {
            ExportMeta meta = exportService.exportScene(sceneId);
            return download(meta);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Scene export failed for sceneId={}: {}", sceneId, e.getMessage(), e);
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    /** Wraps exported bytes in a browser-download response. */
    private Response download(ExportMeta meta) {
        return Response.ok(meta.bytes(), DOCX_MIME)
                .header("Content-Disposition",
                        "attachment; filename=\"" + meta.filename() + "\"")
                .header("Content-Length", meta.bytes().length)
                .build();
    }

    private Response serverError(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(e.getMessage()).build();
    }
}
