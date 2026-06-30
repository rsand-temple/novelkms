package com.richardsand.novelkms.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.ArtifactBlobDao;
import com.richardsand.novelkms.dao.ArtifactBlobDao.BlobRef;
import com.richardsand.novelkms.dao.ArtifactNodeDao;
import com.richardsand.novelkms.model.ArtifactNode;

/**
 * Orchestrates the Artifacts file/folder store: validates Windows-style names,
 * enforces per-file and per-user-quota limits, and keeps the SQL tree and the
 * on-disk blob store consistent. An upload streams to a staging file (enforcing
 * the per-file cap mid-stream), checks the user's quota once the real size is
 * known, then commits the blob row and the file node in one transaction; any
 * failure discards the staged or committed bytes so disk and DB never drift.
 *
 * <p>Trash / restore / purge are delegated to {@code TrashService}, which
 * co-injects this store's DAOs and {@link ArtifactStorage}.
 */
public class ArtifactService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactService.class);

    /** Characters Windows forbids in a file/folder name, plus path separators. */
    private static final String ILLEGAL_NAME_CHARS = "\\/:*?\"<>|";

    private final BasicDataSource ds;
    private final ArtifactNodeDao nodeDao;
    private final ArtifactBlobDao blobDao;
    private final ArtifactStorage storage;
    private final long            maxFileSizeBytes;
    private final long            defaultQuotaBytes;

    public ArtifactService(BasicDataSource ds, ArtifactNodeDao nodeDao, ArtifactBlobDao blobDao,
            ArtifactStorage storage, NovelKmsConfig config) {
        this.ds = ds;
        this.nodeDao = nodeDao;
        this.blobDao = blobDao;
        this.storage = storage;
        NovelKmsConfig.Artifacts a = config.getArtifacts();
        this.maxFileSizeBytes  = a != null ? a.maxFileSizeBytes      : 52_428_800L;     // 50 MB
        this.defaultQuotaBytes = a != null ? a.defaultUserQuotaBytes : 1_073_741_824L;  // 1 GB
    }

    // =========================================================================
    // Typed failures the resource maps to HTTP status + structured body
    // =========================================================================

    /** A business-rule rejection with a stable error code and optional numbers. */
    public static class ArtifactException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int    status;
        private final String code;
        private final Long   maxBytes;
        private final Long   usedBytes;
        private final Long   quotaBytes;
        private final Long   fileBytes;

        public ArtifactException(int status, String code, String message) {
            this(status, code, message, null, null, null, null);
        }

        public ArtifactException(int status, String code, String message,
                Long maxBytes, Long usedBytes, Long quotaBytes, Long fileBytes) {
            super(message);
            this.status = status;
            this.code = code;
            this.maxBytes = maxBytes;
            this.usedBytes = usedBytes;
            this.quotaBytes = quotaBytes;
            this.fileBytes = fileBytes;
        }

        public int status()      { return status; }
        public String code()     { return code; }
        public Long maxBytes()   { return maxBytes; }
        public Long usedBytes()  { return usedBytes; }
        public Long quotaBytes() { return quotaBytes; }
        public Long fileBytes()  { return fileBytes; }
    }

    public record Usage(long usedBytes, long quotaBytes) {
    }

    /** A resolved file ready to stream to the client. */
    public record Download(String name, String contentType, long sizeBytes, InputStream stream) {
    }

    // =========================================================================
    // Reads
    // =========================================================================

    public List<ArtifactNode> tree(UUID projectId) throws SQLException {
        return nodeDao.findLiveByProject(projectId);
    }

    public Usage usage(UUID userId) throws SQLException {
        long used  = blobDao.usedBytes(userId);
        return new Usage(used, resolveQuota(userId));
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    private long resolveQuota(UUID userId) throws SQLException {
        return blobDao.userQuotaOverride(userId).orElse(defaultQuotaBytes);
    }

    // =========================================================================
    // Folder creation
    // =========================================================================

    public ArtifactNode createFolder(UUID projectId, UUID parentId, String rawName) throws SQLException {
        String name = validateName(rawName);
        String norm = normalize(name);
        ArtifactNode parent = resolveParentFolder(projectId, parentId);
        UUID effectiveParent = parent != null ? parent.getId() : null;

        if (nodeDao.nameTakenLive(projectId, effectiveParent, norm, null)) {
            throw nameConflict(name);
        }
        int order = nodeDao.nextDisplayOrder(projectId, effectiveParent);
        ArtifactNode created = nodeDao.insertFolder(projectId, effectiveParent, name, norm, order);
        logger.info("Created artifact folder: projectId={}, parentId={}, nodeId={}",
                projectId, effectiveParent, created.getId());
        return created;
    }

    // =========================================================================
    // File upload
    // =========================================================================

    public ArtifactNode uploadFile(UUID userId, UUID projectId, UUID parentId, String rawFilename,
            String contentType, InputStream in) throws SQLException, IOException {

        String name = validateName(basename(rawFilename));
        String norm = normalize(name);
        String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        ArtifactNode parent = resolveParentFolder(projectId, parentId);
        UUID effectiveParent = parent != null ? parent.getId() : null;

        if (nodeDao.nameTakenLive(projectId, effectiveParent, norm, null)) {
            throw nameConflict(name);
        }

        // Stream to staging, enforcing the per-file cap mid-stream.
        ArtifactStorage.Staged staged;
        try {
            staged = storage.stage(in, maxFileSizeBytes);
        } catch (ArtifactStorage.FileTooLargeException e) {
            throw new ArtifactException(400, "file_too_large",
                    "File exceeds the maximum allowed size.", e.maxBytes(), null, null, null);
        }

        // The staged result is the source of truth for size + sha256.
        final long   sizeBytes = staged.sizeBytes();
        final String sha256    = staged.sha256();
        boolean committed = false;
        String storageKey = null;

        try {
            // Quota check now that the real size is known.
            long used  = blobDao.usedBytes(userId);
            long quota = resolveQuota(userId);
            if (used + sizeBytes > quota) {
                throw new ArtifactException(400, "storage_quota_exceeded",
                        "This upload would exceed your storage quota.", null, used, quota, sizeBytes);
            }

            storageKey = storage.commit(staged);
            committed = true;

            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    BlobRef blob = blobDao.insert(c, userId, sha256, sizeBytes, type, storageKey);
                    int order = nodeDao.nextDisplayOrder(projectId, effectiveParent);
                    ArtifactNode created = nodeDao.insertFile(c, projectId, effectiveParent, name, norm, order,
                            blob.id(), sizeBytes, type);
                    c.commit();
                    logger.info("Uploaded artifact file: projectId={}, parentId={}, nodeId={}, bytes={}",
                            projectId, effectiveParent, created.getId(), sizeBytes);
                    return created;
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException | RuntimeException e) {
            // Roll back side effects: a committed-but-unreferenced blob, or staged bytes.
            if (committed && storageKey != null) {
                storage.delete(storageKey);
            } else {
                storage.discard(staged);
            }
            throw e;
        }
    }

    // =========================================================================
    // Rename / move
    // =========================================================================

    public ArtifactNode rename(UUID nodeId, String rawName) throws SQLException {
        ArtifactNode node = nodeDao.findLiveById(nodeId)
                .orElseThrow(() -> new ArtifactException(404, "not_found", "Artifact not found."));
        String name = validateName(rawName);
        String norm = normalize(name);
        if (nodeDao.nameTakenLive(node.getProjectId(), node.getParentId(), norm, nodeId)) {
            throw nameConflict(name);
        }
        nodeDao.rename(nodeId, name, norm);
        return nodeDao.findLiveById(nodeId).orElse(node);
    }

    public ArtifactNode move(UUID nodeId, UUID newParentId) throws SQLException {
        ArtifactNode node = nodeDao.findLiveById(nodeId)
                .orElseThrow(() -> new ArtifactException(404, "not_found", "Artifact not found."));
        UUID projectId = node.getProjectId();

        ArtifactNode parent = resolveParentFolder(projectId, newParentId);
        UUID effectiveParent = parent != null ? parent.getId() : null;

        if (effectiveParent != null && nodeDao.wouldCycle(nodeId, effectiveParent)) {
            throw new ArtifactException(400, "invalid_move", "A folder cannot be moved inside itself.");
        }
        if (nodeDao.nameTakenLive(projectId, effectiveParent, normalize(node.getName()), nodeId)) {
            throw nameConflict(node.getName());
        }
        int order = nodeDao.nextDisplayOrder(projectId, effectiveParent);
        nodeDao.move(nodeId, effectiveParent, order);
        return nodeDao.findLiveById(nodeId).orElse(node);
    }

    // =========================================================================
    // Download
    // =========================================================================

    public Download download(UUID nodeId) throws SQLException, IOException {
        ArtifactNode node = nodeDao.findLiveById(nodeId)
                .orElseThrow(() -> new ArtifactException(404, "not_found", "Artifact not found."));
        if (!ArtifactNodeDao.TYPE_FILE.equals(node.getType())) {
            throw new ArtifactException(400, "not_a_file", "Folders cannot be downloaded.");
        }
        UUID blobId = nodeDao.blobIdOf(nodeId)
                .orElseThrow(() -> new ArtifactException(404, "not_found", "File contents are missing."));
        BlobRef blob = blobDao.findById(blobId)
                .orElseThrow(() -> new ArtifactException(404, "not_found", "File contents are missing."));
        InputStream stream = storage.open(blob.storageKey());
        return new Download(node.getName(), node.getContentType(), node.getSizeBytes(), stream);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ArtifactNode resolveParentFolder(UUID projectId, UUID parentId) throws SQLException {
        if (parentId == null) {
            return null; // project root
        }
        ArtifactNode parent = nodeDao.findLiveById(parentId)
                .orElseThrow(() -> new ArtifactException(400, "invalid_parent", "Parent folder not found."));
        if (!parent.getProjectId().equals(projectId)) {
            throw new ArtifactException(400, "invalid_parent", "Parent folder belongs to a different project.");
        }
        if (!ArtifactNodeDao.TYPE_FOLDER.equals(parent.getType())) {
            throw new ArtifactException(400, "invalid_parent", "Files cannot contain other items.");
        }
        return parent;
    }

    private static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ArtifactException(400, "invalid_name", "A name is required.");
        }
        if (name.equals(".") || name.equals("..")) {
            throw new ArtifactException(400, "invalid_name", "That name is not allowed.");
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 0x20 || ILLEGAL_NAME_CHARS.indexOf(ch) >= 0) {
                throw new ArtifactException(400, "invalid_name",
                        "A name may not contain any of: \\ / : * ? \" < > | or control characters.");
            }
        }
        if (name.endsWith(".") || name.endsWith(" ")) {
            throw new ArtifactException(400, "invalid_name", "A name may not end with a space or a period.");
        }
        if (name.length() > 255) {
            throw new ArtifactException(400, "invalid_name", "That name is too long.");
        }
        return name;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Strips any client-supplied path components, keeping only the final segment. */
    private static String basename(String raw) {
        if (raw == null) {
            return "file";
        }
        String s = raw.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        String base = slash >= 0 ? s.substring(slash + 1) : s;
        return base.isBlank() ? "file" : base;
    }

    private static ArtifactException nameConflict(String name) {
        return new ArtifactException(409, "name_conflict",
                "An item named \"" + name + "\" already exists here.");
    }
}
