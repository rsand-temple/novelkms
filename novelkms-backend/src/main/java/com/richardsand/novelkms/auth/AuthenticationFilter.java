package com.richardsand.novelkms.auth;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "auth/",
            "healthcheck",
            "billing/stripe/webhook");

    private final SessionService     sessions;

    @Inject
    public AuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String path = request.getUriInfo().getPath();
        if ("OPTIONS".equals(request.getMethod()) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            logger.debug("Auto authorizing {} {}", path, request.getMethod());
            return;
        }
        Cookie cookie = request.getCookies().get(AuthConstants.SESSION_COOKIE);
        try {
            var user = sessions.authenticate(cookie == null ? null : cookie.getValue());
            if (user.isEmpty()) {
                logger.debug("No session found for {} {}", path, request.getMethod());
                request.abortWith(Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON).entity("{\"error\":\"authentication_required\"}").build());
                return;
            }
            request.setProperty(AuthConstants.REQUEST_USER_ID, user.get().id());
            logger.debug("Authenticated {} for {} {}", user.get().normalizedEmail(), path, request.getMethod());
        } catch (Exception e) {
            throw new IOException("Session authentication failed", e);
        }
    }
}
