package com.richardsand.novelkms.model.admin;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserDetail(
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
        Instant lastLoginAt,
        Set<String> roles,
        AdminUserSubscriptionSummary subscription,
        AdminUserUsageSummary usage) {
}