package com.richardsand.novelkms.resource;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.service.EpubExportService;
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
 * Download endpoints for exporting manuscript content.
 *
 * Word (.docx) supports book, part, chapter, and scene scopes.
 * EPUB is intentionally book-only: it is a whole-book reading-copy export.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {

    private static final Logger logger = LoggerFactory.getLogger(ExportResource.class);

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String EPUB_MIME = "application/epub+zip";

    private final ExportService     exportService;
    private final EpubExportService epubExportService;

    @Inject
    public ExportResource(ExportService exportService, EpubExportService epubExportService) {
        this.exportService = exportService;
        this.epubExportService = epubExportService;
    }

    // -------------------------------------------------------------------------
    // Book
    // -------------------------------------------------------------------------

    @GET
    @Path("/export/books/{bookId}/docx")
    @Produces(MediaType.WILDCARD)
    public Response exportBook(@PathParam("bookId") UUID bookId) {
        logger.info("ExportResource.exportBook invoked: bookId={}", bookId);
        try {
            ExportMeta meta = exportService.exportBook(bookId);
            return download(meta.bytes(), meta.filename(), DOCX_MIME);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Book DOCX export failed for bookId={}: {}", bookId, e.getMessage(), e);
            return serverError(e);
        }
    }

    @GET
    @Path("/export/books/{bookId}/epub")
    @Produces(MediaType.WILDCARD)
    public Response exportBookEpub(@PathParam("bookId") UUID bookId) {
        logger.info("ExportResource.exportBookEpub invoked: bookId={}", bookId);
        try {
            EpubExportService.ExportMeta meta = epubExportService.exportBook(bookId);
            return download(meta.bytes(), meta.filename(), EPUB_MIME);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Book EPUB export failed for bookId={}: {}", bookId, e.getMessage(), e);
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
        logger.info("ExportResource.exportPart invoked: partId={}", partId);
        try {
            ExportMeta meta = exportService.exportPart(partId);
            return download(meta.bytes(), meta.filename(), DOCX_MIME);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
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
        logger.info("ExportResource.exportChapter invoked: chapterId={}", chapterId);
        try {
            ExportMeta meta = exportService.exportChapter(chapterId);
            return download(meta.bytes(), meta.filename(), DOCX_MIME);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
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
        logger.info("ExportResource.exportScene invoked: sceneId={}", sceneId);
        try {
            ExportMeta meta = exportService.exportScene(sceneId);
            return download(meta.bytes(), meta.filename(), DOCX_MIME);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Scene export failed for sceneId={}: {}", sceneId, e.getMessage(), e);
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    /** Wraps exported bytes in a browser-download response. */
    private Response download(byte[] bytes, String filename, String mimeType) {
        return Response.ok(bytes, mimeType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Length", bytes.length)
                .build();
    }

    private Response serverError(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(e.getMessage()).build();
    }
}
