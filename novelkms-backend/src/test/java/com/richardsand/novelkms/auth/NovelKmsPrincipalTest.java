package com.richardsand.novelkms.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NovelKmsPrincipalTest {

    @Test
    void getNameReturnsUserId() {
        UUID userId = UUID.randomUUID();

        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                userId,
                "admin@example.com",
                "Admin User",
                Set.of(Roles.ADMIN));

        assertEquals(userId.toString(), principal.getName());
        assertEquals(userId, principal.id());
        assertEquals("admin@example.com", principal.email());
        assertEquals("Admin User", principal.displayName());
    }

    @Test
    void adminRoleIsRecognized() {
        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                UUID.randomUUID(),
                "admin@example.com",
                "Admin User",
                Set.of(Roles.ADMIN));

        assertTrue(principal.isInRole(Roles.ADMIN));
        assertTrue(principal.isAdmin());
        assertFalse(principal.isInRole("SUPPORT"));
    }

    @Test
    void nullRolesBecomeEmptySet() {
        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                UUID.randomUUID(),
                "user@example.com",
                "Normal User",
                null);

        assertTrue(principal.roles().isEmpty());
        assertFalse(principal.isInRole(Roles.ADMIN));
        assertFalse(principal.isAdmin());
    }

    @Test
    void rolesAreDefensivelyCopied() {
        Set<String> roles = new HashSet<>();
        roles.add(Roles.ADMIN);

        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                UUID.randomUUID(),
                "admin@example.com",
                "Admin User",
                roles);

        roles.clear();

        assertTrue(principal.isAdmin());
        assertTrue(principal.isInRole(Roles.ADMIN));
    }

    @Test
    void rolesAreImmutable() {
        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                UUID.randomUUID(),
                "admin@example.com",
                "Admin User",
                Set.of(Roles.ADMIN));

        assertThrows(UnsupportedOperationException.class, () -> principal.roles().add("SUPPORT"));
    }

    @Test
    void nullRoleCheckReturnsFalse() {
        NovelKmsPrincipal principal = new NovelKmsPrincipal(
                UUID.randomUUID(),
                "admin@example.com",
                "Admin User",
                Set.of(Roles.ADMIN));

        assertFalse(principal.isInRole(null));
    }
}