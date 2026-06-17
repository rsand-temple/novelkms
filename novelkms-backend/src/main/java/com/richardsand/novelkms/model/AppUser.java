package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
        UUID id,
        String emailAddress,
        String normalizedEmail,
        boolean emailVerified,
        String firstName,
        String lastName,
        String displayName,
        String mobileNumber,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {
}
