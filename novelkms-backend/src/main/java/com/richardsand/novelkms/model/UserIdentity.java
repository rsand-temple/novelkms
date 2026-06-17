package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

public record UserIdentity(
        UUID id,
        UUID userId,
        String provider,
        String providerSubject,
        String providerEmail,
        boolean providerEmailVerified,
        Instant createdAt,
        Instant lastLoginAt) {
}
