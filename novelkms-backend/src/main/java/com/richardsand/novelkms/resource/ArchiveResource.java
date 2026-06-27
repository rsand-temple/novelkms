package com.richardsand.novelkms.resource;

import java.io.InputStream;
import java.util.UUID;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.TenantAccessDao;
import com.richardsand.novelkms.service.ArchiveService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * User-facing NovelKMS archive portability endpoints.
 *
 * V1 is project-level export/import-as-new-project. It deliberately does not
 * merge into existing projects, replace existing projects, or move credentials /
 * OAuth/session secrets between accounts.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ArchiveResource {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveResource.class);

    private final ArchiveService archiveService;
    private final TenantAccessDao access;

    @Inject
    public ArchiveResource(ArchiveService archiveService, TenantAccessDao access) {
        this.archiveService = archiveService;
        this.access = access;
    }

    @GET
    @Path("/export/projects/{projectId}/kms")
    @Produces(MediaType.WILDCARD)
    public Response exportProjectArchive(
            @PathParam("projectId") UUID projectId,
            @Context ContainerRequestContext request) {
        UUID userId = CurrentUser.id(request);
        logger.info("User {} exporting project {}", userId, projectId);
        try {
            if (!access.ownsProject(userId, projectId)) {
                return Response.status(Response.Status.NO_CONTENT).build();
            }
            ArchiveService.ExportMeta meta = archiveService.exportProject(userId, projectId);
            return Response.ok(meta.bytes(), ArchiveService.MIME_TYPE)
                    .header("Content-Disposition", "attachment; filename=\"" + meta.filename() + "\"")
                    .header("Content-Length", meta.bytes().length)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("NovelKMS project export failed: projectId={}, error={}", projectId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("NovelKMS export failed: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/import/kms/validate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response validateArchive(
            @FormDataParam("file") InputStream fileStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        if (fileStream == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("file is required").build();
        }
        try {
            logger.info("KMS archive validation requested: filename={}",
                    fileDetail != null ? fileDetail.getFileName() : null);
            return Response.ok(archiveService.preview(fileStream)).build();
        } catch (Exception e) {
            logger.warn("KMS archive validation failed: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Archive validation failed: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/import/kms")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importArchive(
            @FormDataParam("file") InputStream fileStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail,
            @Context ContainerRequestContext request) {
        if (fileStream == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("file is required").build();
        }
        UUID userId = CurrentUser.id(request);
        try {
            logger.info("KMS archive import requested: userId={}, filename={}",
                    userId, fileDetail != null ? fileDetail.getFileName() : null);
            ArchiveService.ImportResult result = archiveService.importAsNewProjects(userId, fileStream);
            logger.info("KMS archive import completed: userId={}, projects={}, books={}, chapters={}, scenes={}",
                    userId, result.projectCount(), result.bookCount(), result.chapterCount(), result.sceneCount());
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("KMS archive import failed: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("KMS import failed: " + e.getMessage())
                    .build();
        }
    }
}
