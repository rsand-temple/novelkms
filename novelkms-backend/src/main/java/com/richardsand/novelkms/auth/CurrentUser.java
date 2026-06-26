package com.richardsand.novelkms.auth;

import java.util.UUID;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrentUser {
    public static UUID id(ContainerRequestContext request) {
        Object value = request.getProperty(AuthConstants.REQUEST_USER_ID);
        if (value instanceof UUID id)
            return id;
        if (value instanceof String text)
            return UUID.fromString(text);
        throw new NotAuthorizedException("Authenticated user is missing from request context");
    }
}
