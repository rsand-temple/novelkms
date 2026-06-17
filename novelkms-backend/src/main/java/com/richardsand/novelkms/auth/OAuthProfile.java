package com.richardsand.novelkms.auth;

public record OAuthProfile(
        String provider,
        String subject,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName) {
}
