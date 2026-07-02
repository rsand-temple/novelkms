package com.richardsand.novelkms.resource;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.TenantAccessDao;
import com.richardsand.novelkms.model.ArtifactNode;
import com.richardsand.novelkms.service.ArtifactService;
import com.richardsand.novelkms.service.ArtifactService.ArtifactException;
import com.richardsand.novelkms.service.ArtifactService.Download;
import com.richardsand.novelkms.service.ArtifactService.Usage;
import com.richardsand.novelkms.service.TrashService;

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
import jakarta.ws.rs.core.StreamingOutput;

/**
 * The Artifacts file/folder store endpoints — a per-project area for
 * non-manuscript material (query letters, research, cover-art sources). No
 * in-app rendering: files are download-only.
 *
 * <p>Authorization: project-rooted paths ({@code projects/{id}/...}) are checked
 * by the tenant filter on the {@code projects/{uuid}} segment. The node- and
 * file-scoped paths ({@code artifacts/nodes/{id}}, {@code artifacts/files/{id}}),
 * and the export path, carry only their own UUID, which the filter does not
 * recognize, so this resource enforces ownership in-line via
 * {@link TenantAccessDao#ownsArtifactNode} (the established "UUID-only paths
 * enforce ownership in the resource" rule). The zip-export path is project-rooted
 * and therefore covered by the tenant filter on the {@code projects/{uuid}} segment.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ArtifactResource {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactResource.class);

    private final ArtifactService artifacts;
    private final TenantAccessDao access;
    private final TrashService    trash;

    @Inject
    public ArtifactResource(ArtifactService artifacts, TenantAccessDao access, TrashService trash) {
        this.artifacts = artifacts;
        this.access = access;
        this.trash = trash;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateFolderRequest {
        @JsonProperty public String parentId;
        @JsonProperty public String name;
    }

    public static class RenameRequest {
        @JsonProperty public String name;
    }

    public static class MoveRequest {
        @JsonProperty public String parentId;
    }

    // -------------------------------------------------------------------------
    // Project-scoped reads (tenant filter authorizes the projects/{id} segment)
    // -------------------------------------------------------------------------

    @GET
    @Path("/projects/{projectId}/artifacts")
    public Response tree(@PathParam("projectId") UUID projectId) {
        try {
            return Response.ok(Map.of("nodes", artifacts.tree(projectId))).build();
        } catch (SQLException e) {
            return serverError(e, "list artifacts");
        }
    }

    @GET
    @Path("/projects/{projectId}/artifacts/usage")
    public Response usage(@PathParam("projectId") UUID projectId,
            @Context ContainerRequestContext request) {
        try {
            Usage u = artifacts.usage(CurrentUser.id(request));
            return Response.ok(Map.of(
                    "usedBytes", u.usedBytes(),
                    "quotaBytes", u.quotaBytes(),
                    "maxFileSizeBytes", artifacts.maxFileSizeBytes())).build();
        } catch (SQLException e) {
            return serverError(e, "read storage usage");
        }
    }

    // -------------------------------------------------------------------------
    // Zip export (project-scoped; tenant filter covers the projects/{uuid} segment)
    // -------------------------------------------------------------------------

    /**
     * Streams all live artifacts for the project as a zip archive. The hierarchy
     * is preserved: a file at {@code Research/Notes/ref.pdf} appears at that path
     * inside the zip. Folder entries are included so empty directories survive
     * extraction. The zip is assembled on the fly — no temp file is written.
     */
    @GET
    @Path("/projects/{projectId}/artifacts/export")
    @Produces("application/zip")
    public Response export(@PathParam("projectId") UUID projectId) {
        try {
            String filename = artifacts.zipFilename(projectId);
            StreamingOutput body = out -> {
                try {
                    artifacts.exportZip(projectId, out);
                } catch (SQLException e) {
                    throw new RuntimeException("Database error during artifact export", e);
                }
            };
            return Response.ok(body)
                    .type("application/zip")
                    .header("Content-Disposition", "attachment; filename=\"" + sanitizeHeader(filename) + "\"")
                    .build();
        } catch (SQLException e) {
            return serverError(e, "export artifacts");
        }
    }

    // -------------------------------------------------------------------------
    // Create folder / upload file (project-scoped path)
    // -------------------------------------------------------------------------

    @POST
    @Path("/projects/{projectId}/artifacts/folders")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createFolder(@PathParam("projectId") UUID projectId, CreateFolderRequest body) {
        try {
            UUID parentId = parseNullableUuid(body == null ? null : body.parentId);
            ArtifactNode node = artifacts.createFolder(projectId, parentId, body == null ? null : body.name);
            return Response.ok(node).build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "create folder");
        }
    }

    @POST
    @Path("/projects/{projectId}/artifacts/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@PathParam("projectId") UUID projectId,
            @FormDataParam("parentId") String parentIdText,
            @FormDataParam("file") InputStream fileStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail,
            @FormDataParam("file") FormDataBodyPart filePart,
            @Context ContainerRequestContext request) {
        if (fileStream == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "missing_file", "message", "A file is required.")).build();
        }
        try {
            UUID   parentId    = parseNullableUuid(parentIdText);
            String filename    = (fileDetail != null && fileDetail.getFileName() != null)
                    ? fileDetail.getFileName() : "file";
            String contentType = (filePart != null && filePart.getMediaType() != null)
                    ? filePart.getMediaType().toString() : null;

            ArtifactNode node = artifacts.uploadFile(
                    CurrentUser.id(request), projectId, parentId, filename, contentType, fileStream);
            return Response.ok(node).build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "upload file");
        } catch (Exception e) {
            logger.error("Artifact upload failed: {}", e.getMessage(), e);
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    // -------------------------------------------------------------------------
    // Node-scoped operations (UUID-only path → in-resource ownership check)
    // -------------------------------------------------------------------------

    @PUT
    @Path("/artifacts/nodes/{nodeId}/rename")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response rename(@PathParam("nodeId") UUID nodeId, RenameRequest body,
            @Context ContainerRequestContext request) {
        Response denied = requireOwnership(nodeId, request);
        if (denied != null) {
            return denied;
        }
        try {
            return Response.ok(artifacts.rename(nodeId, body == null ? null : body.name)).build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "rename artifact");
        }
    }

    @PUT
    @Path("/artifacts/nodes/{nodeId}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@PathParam("nodeId") UUID nodeId, MoveRequest body,
            @Context ContainerRequestContext request) {
        Response denied = requireOwnership(nodeId, request);
        if (denied != null) {
            return denied;
        }
        try {
            UUID parentId = parseNullableUuid(body == null ? null : body.parentId);
            return Response.ok(artifacts.move(nodeId, parentId)).build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "move artifact");
        }
    }

    @DELETE
    @Path("/artifacts/nodes/{nodeId}")
    public Response delete(@PathParam("nodeId") UUID nodeId,
            @Context ContainerRequestContext request) {
        UUID userId = CurrentUser.id(request);
        try {
            // Ownership is enforced inside trashArtifactNode (owner_user_id guard);
            // a non-owned or missing node yields an empty result -> idempotent 204.
            return trash.trashArtifactNode(userId, nodeId)
                    .map(item -> Response.ok(item).build())
                    .orElse(Response.noContent().build());
        } catch (SQLException e) {
            return serverError(e, "trash artifact");
        }
    }

    // -------------------------------------------------------------------------
    // Download (UUID-only path → in-resource ownership check)
    // -------------------------------------------------------------------------

    @GET
    @Path("/artifacts/files/{nodeId}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("nodeId") UUID nodeId,
            @Context ContainerRequestContext request) {
        Response denied = requireOwnership(nodeId, request);
        if (denied != null) {
            return denied;
        }
        try {
            Download d = artifacts.download(nodeId);
            StreamingOutput body = out -> {
                try (InputStream in = d.stream()) {
                    in.transferTo(out);
                }
            };
            return Response.ok(body)
                    .type(d.contentType() != null ? d.contentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Length", d.sizeBytes())
                    .header("Content-Disposition", "attachment; filename=\"" + sanitizeHeader(d.name()) + "\"")
                    .build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "download artifact");
        } catch (Exception e) {
            logger.error("Artifact download failed: {}", e.getMessage(), e);
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    // -------------------------------------------------------------------------
    // In-place text save (UUID-only path → in-resource ownership check)
    // -------------------------------------------------------------------------

    @PUT
    @Path("/artifacts/files/{nodeId}/content")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveText(@PathParam("nodeId") UUID nodeId, String body,
            @Context ContainerRequestContext request) {
        Response denied = requireOwnership(nodeId, request);
        if (denied != null) {
            return denied;
        }
        try {
            return Response.ok(artifacts.replaceText(CurrentUser.id(request), nodeId, body)).build();
        } catch (ArtifactException e) {
            return artifactError(e);
        } catch (SQLException e) {
            return serverError(e, "save text artifact");
        } catch (Exception e) {
            logger.error("Artifact text save failed: {}", e.getMessage(), e);
            return Response.serverError().entity(Map.of("error", "server_error")).build();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns a 404 Response if the caller does not own the node, else null. */
    private Response requireOwnership(UUID nodeId, ContainerRequestContext request) {
        try {
            if (!access.ownsArtifactNode(CurrentUser.id(request), nodeId)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "not_found")).build();
            }
            return null;
        } catch (SQLException e) {
            return serverError(e, "authorize artifact");
        }
    }

    private static UUID parseNullableUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            throw new ArtifactException(400, "invalid_parent", "parentId is not a valid id.");
        }
    }

    private static String sanitizeHeader(String name) {
        // Strip characters that would break a Content-Disposition header value.
        return name == null ? "download" : name.replaceAll("[\"\\r\\n]", "_");
    }

    private Response artifactError(ArtifactException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.code());
        body.put("message", e.getMessage());
        if (e.maxBytes() != null)   body.put("maxBytes", e.maxBytes());
        if (e.usedBytes() != null)  body.put("usedBytes", e.usedBytes());
        if (e.quotaBytes() != null) body.put("quotaBytes", e.quotaBytes());
        if (e.fileBytes() != null)  body.put("fileBytes", e.fileBytes());
        return Response.status(e.status()).entity(body).build();
    }

    private Response serverError(SQLException e, String action) {
        logger.error("Database error during {}: {}", action, e.getMessage(), e);
        return Response.serverError().entity(Map.of("error", "server_error")).build();
    }
}
