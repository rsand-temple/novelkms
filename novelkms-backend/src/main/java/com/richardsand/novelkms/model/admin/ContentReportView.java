package com.richardsand.novelkms.model.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * The admin-console view of a moderation report (slice 1F).
 *
 * <p>Distinct from the raw {@code ContentReport} persistence model: it resolves the
 * reporter to their public {@code reporterHandle} rather than surfacing the raw
 * {@code app_user} id, keeping the review network's "handles, not user ids" keyhole
 * intact even on the admin surface. {@code targetId} is retained because the admin
 * acts on it (remove that request/review, suspend that profile).
 *
 * <p>A record, matching the other admin wire DTOs.
 */
public record ContentReportView(
        UUID id,
        String reporterHandle,
        String targetType,
        UUID targetId,
        String reason,
        String detail,
        String status,
        String resolutionNote,
        Instant createdAt,
        Instant resolvedAt) {
}
