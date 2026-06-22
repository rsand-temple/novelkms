package com.richardsand.novelkms.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Symmetric encryption for secrets stored at rest (currently BYOK AI provider
 * API keys). Uses AES-256-GCM with a random 96-bit IV per message and a 128-bit
 * authentication tag.
 *
 * <p>Wire format of {@link #encrypt(String)} output:
 * <pre>v1:BASE64( IV(12 bytes) || CIPHERTEXT+TAG )</pre>
 * The {@code v1:} prefix reserves room for future key rotation / algorithm
 * changes without ambiguity.
 *
 * <p>The master key is supplied via configuration ({@code security.encryptionKey},
 * normally injected from the {@code NOVELKMS_ENCRYPTION_KEY} environment
 * variable). Acceptable forms:
 * <ul>
 *   <li>A Base64-encoded 16/24/32-byte key — used directly.</li>
 *   <li>Any other non-blank passphrase — hashed with SHA-256 to derive a
 *       32-byte key, so an operator can set a human passphrase and still get a
 *       valid AES-256 key.</li>
 *   <li>Blank/null — a fixed development key is used and a prominent warning is
 *       logged. This keeps local Eclipse runs working out of the box but MUST
 *       be overridden in any deployment, or stored secrets are not protected.</li>
 * </ul>
 */
public final class SecretCipher {
    private static final Logger logger = LoggerFactory.getLogger(SecretCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX         = "v1:";
    private static final int    IV_BYTES       = 12;
    private static final int    TAG_BITS       = 128;

    // Development-only fallback key. NEVER relied upon in deployment.
    private static final byte[] DEV_KEY = sha256("novelkms-development-only-encryption-key");

    private final SecretKeySpec key;
    private final SecureRandom  random = new SecureRandom();

    public SecretCipher(String configuredKey) {
        this.key = new SecretKeySpec(resolveKeyBytes(configuredKey), "AES");
    }

    private static byte[] resolveKeyBytes(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            logger.warn("security.encryptionKey is not set — using an INSECURE development key. "
                    + "Set NOVELKMS_ENCRYPTION_KEY before storing real secrets.");
            return DEV_KEY;
        }
        // Prefer a Base64-encoded raw AES key of a valid length.
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Not Base64 — fall through to passphrase derivation.
        }
        // Otherwise treat the value as a passphrase and derive a 256-bit key.
        return sha256(configuredKey);
    }

    /** Encrypts plaintext, returning the {@code v1:}-prefixed Base64 token. */
    public String encrypt(String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext must not be null");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /** Decrypts a token produced by {@link #encrypt(String)}. */
    public String decrypt(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            throw new IllegalStateException("Unrecognized secret token format");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            if (raw.length <= IV_BYTES) {
                throw new IllegalStateException("Secret token is too short");
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_BYTES);
            byte[] ct = Arrays.copyOfRange(raw, IV_BYTES, raw.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
