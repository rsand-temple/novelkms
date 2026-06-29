package com.richardsand.novelkms.auth;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record NovelKmsPrincipal(
        UUID id,
        String email,
        String displayName,
        Set<String> roles) implements Principal {

    public NovelKmsPrincipal {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(Set.copyOf(roles));
    }

    @Override
    public String getName() {
        return id.toString();
    }

    public boolean isInRole(String role) {
        return role != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return isInRole(Roles.ADMIN);
    }
}