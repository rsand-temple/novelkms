package com.richardsand.novelkms.auth;

import java.security.Principal;
import java.util.UUID;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrentUser {
    public static UUID id(ContainerRequestContext request) {
        NovelKmsPrincipal principal = principal(request);
        if (principal != null) {
            return principal.id();
        }

        Object value = request.getProperty(AuthConstants.REQUEST_USER_ID);
        if (value instanceof UUID id) {
            return id;
        }
        if (value instanceof String text) {
            return UUID.fromString(text);
        }
        throw new NotAuthorizedException("Authenticated user is missing from request context");
    }

    public static NovelKmsPrincipal principal(ContainerRequestContext request) {
        Object value = request.getProperty(AuthConstants.REQUEST_PRINCIPAL);
        if (value instanceof NovelKmsPrincipal novelKmsPrincipal) {
            return novelKmsPrincipal;
        }

        Principal securityPrincipal = request.getSecurityContext() == null
                ? null
                : request.getSecurityContext().getUserPrincipal();

        if (securityPrincipal instanceof NovelKmsPrincipal novelKmsPrincipal) {
            return novelKmsPrincipal;
        }

        return null;
    }

    public static boolean hasRole(ContainerRequestContext request, String role) {
        return request.getSecurityContext() != null && request.getSecurityContext().isUserInRole(role);
    }

    public static boolean isAdmin(ContainerRequestContext request) {
        return hasRole(request, Roles.ADMIN);
    }
}