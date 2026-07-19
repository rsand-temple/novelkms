package com.richardsand.novelkms.model.review;

import java.time.Instant;

/**
 * One entry in a user's own block list (slice 1F).
 *
 * <p>Identifies the blocked user by their public {@code handle} only — never by
 * {@code user_id}, the same keyhole every cross-user DTO in the review network
 * honors. A handle is all the UI needs to render the row and to undo the block via
 * {@code DELETE /review/blocks/{handle}}.
 *
 * <p>A record rather than a Lombok class, matching the other small wire DTOs
 * ({@code GrantFamilyAccessRequest}, {@code AdminAuditLogEntry}); Jackson
 * serializes it by component accessor with no extra configuration.
 */
public record BlockedUser(
        String handle,
        String displayName,
        Instant blockedAt) {
}
