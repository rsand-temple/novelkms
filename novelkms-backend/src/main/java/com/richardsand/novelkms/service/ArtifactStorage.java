package com.richardsand.novelkms.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The on-disk blob store for artifact file bytes. The logical tree (names,
 * hierarchy, trash) lives in SQL; this class owns nothing but opaque bytes keyed
 * by a sharded storage key, so renames/moves never touch disk and a future
 * content-addressed/versioned store can replace the key scheme additively.
 *
 * <p>Uploads are streamed to a staging file first (never buffered whole in heap)
 * so the size cap can be enforced mid-stream and the real size/sha256 are known
 * before the quota check. Only on commit is the staged file atomically moved
 * into its final sharded location — staging and final live under the same root
 * so the move is a same-filesystem rename.
 *
 * <p>If no storage directory is configured, a temp directory under
 * {@code java.io.tmpdir} is used and a warning is logged — fine for local
 * Eclipse runs, never for a real deployment (the same dev-fallback idiom as the
 * INSECURE encryption key).
 */
public class ArtifactStorage {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactStorage.class);

    private final Path root;
    private final Path staging;

    /** Result of streaming an upload into the staging area, pre-commit. */
    public record Staged(Path tempPath, String sha256, long sizeBytes) {
    }

    /** Thrown when an upload exceeds the per-file byte cap mid-stream. */
    public static class FileTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
        private final long maxBytes;

        public FileTooLargeException(long maxBytes) {
            super("File exceeds the maximum allowed size of " + maxBytes + " bytes");
            this.maxBytes = maxBytes;
        }

        public long maxBytes() {
            return maxBytes;
        }
    }

    public ArtifactStorage(String storageDir) {
        Path base;
        if (storageDir == null || storageDir.isBlank()) {
            base = Path.of(System.getProperty("java.io.tmpdir"), "novelkms-artifacts");
            logger.warn("artifacts.storageDir is not configured — using INSECURE temporary blob store at {}. "
                    + "Set NOVELKMS_ARTIFACT_DIR to a host-mounted volume for any real deployment.", base);
        } else {
            base = Path.of(storageDir);
        }
        this.root = base;
        this.staging = base.resolve(".staging");
        try {
            Files.createDirectories(this.root);
            Files.createDirectories(this.staging);
        } catch (IOException e) {
            logger.error("Could not create artifact storage directories under {}: {}", base, e.getMessage(), e);
            throw new IllegalStateException("Unable to initialize artifact storage at " + base, e);
        }
        logger.info("Artifact blob store initialized at {}", this.root);
    }

    /**
     * Streams an upload into the staging area, computing its SHA-256 and size and
     * aborting (and cleaning up) if it grows past {@code maxBytes}. The returned
     * staged file must be either {@link #commit committed} or
     * {@link #discard discarded} by the caller.
     */
    public Staged stage(InputStream in, long maxBytes) throws IOException {
        Path temp = staging.resolve(UUID.randomUUID().toString());
        MessageDigest digest = newDigest();
        long total = 0;
        boolean ok = false;
        try (OutputStream out = Files.newOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new FileTooLargeException(maxBytes);
                }
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
            ok = true;
        } finally {
            if (!ok) {
                deleteQuietly(temp);
            }
        }
        return new Staged(temp, toHex(digest.digest()), total);
    }

    /**
     * Moves a staged file into its final sharded location and returns the opaque
     * storage key recorded on the blob row. Sharded by the first two hex
     * characters of a fresh blob id to avoid one enormous flat directory.
     */
    public String commit(Staged staged) throws IOException {
        UUID   blobId = UUID.randomUUID();
        String hex    = blobId.toString().replace("-", "");
        String shard  = hex.substring(0, 2);
        String key    = shard + "/" + blobId;

        Path dir = root.resolve(shard);
        Files.createDirectories(dir);
        Path dest = root.resolve(key);
        Files.move(staged.tempPath(), dest, StandardCopyOption.ATOMIC_MOVE);
        return key;
    }

    /** Discards a staged file that will not be committed (cap/quota rejection). */
    public void discard(Staged staged) {
        if (staged != null) {
            deleteQuietly(staged.tempPath());
        }
    }

    /** Opens a committed blob for download. */
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(root.resolve(storageKey));
    }

    /**
     * Best-effort permanent removal of a committed blob (called on purge, after
     * the DB transaction has committed). A leftover file is harmless — it is
     * unreferenced bytes, not a correctness problem — so failures are logged, not
     * thrown.
     */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(storageKey));
        } catch (IOException e) {
            logger.warn("Could not delete artifact blob {} from disk: {}", storageKey, e.getMessage());
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            logger.warn("Could not delete staged artifact file {}: {}", p, e.getMessage());
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
