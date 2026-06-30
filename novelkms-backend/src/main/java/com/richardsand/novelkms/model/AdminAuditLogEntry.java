package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogEntry(
        UUID id,
        UUID adminUserId,
        UUID targetUserId,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String reason,
        Instant createdAt) {
}