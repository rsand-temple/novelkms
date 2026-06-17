package com.richardsand.novelkms.auth;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, String displayName, String emailAddress) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
