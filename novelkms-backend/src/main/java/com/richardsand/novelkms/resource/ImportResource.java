package com.richardsand.novelkms.resource;

import java.io.InputStream;
import java.util.UUID;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.service.ImportService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    private static final Logger logger = LoggerFactory.getLogger(ImportResource.class);

    private final ImportService importService;

    @Inject
    public ImportResource(ImportService importService) {
        this.importService = importService;
    }

    /**
     * POST /api/import/docx
     *
     * Multipart form upload. Fields:
     *   projectId  — UUID of the target project (required)
     *   bookTitle  — optional title override; cover page title takes priority
     *                over filename fallback but not over this explicit value
     *   file       — the .docx binary
     *
     * Returns ImportService.ImportResult as JSON on success (200 OK).
     */
    @POST
    @Path("/import/docx")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importDocx(
            @FormDataParam("projectId")  String                   projectIdStr,
            @FormDataParam("bookTitle")  String                   bookTitle,
            @FormDataParam("file")       InputStream              fileStream,
            @FormDataParam("file")       FormDataContentDisposition fileDetail) {

        if (projectIdStr == null || projectIdStr.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("projectId is required").build();
        }
        if (fileStream == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("file is required").build();
        }

        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdStr.trim());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("projectId is not a valid UUID").build();
        }

        String filename = (fileDetail != null && fileDetail.getFileName() != null)
                ? fileDetail.getFileName()
                : "import.docx";

        logger.info("DOCX import request: projectId={}, filename={}", projectId, filename);

        try {
            ImportService.ImportResult result = importService.importDocx(
                    projectId,
                    bookTitle,
                    filename,
                    fileStream);
            return Response.ok(result).build();
        } catch (Exception e) {
            logger.error("DOCX import failed: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Import failed: " + e.getMessage()).build();
        }
    }
}
