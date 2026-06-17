package com.richardsand.novelkms.resource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.auth.AuthConstants;
import com.richardsand.novelkms.auth.CryptoTokens;
import com.richardsand.novelkms.auth.OAuthService;
import com.richardsand.novelkms.auth.SessionService;
import com.richardsand.novelkms.dao.AuthDao;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    public record RegistrationRequest(@NotBlank String displayName, String firstName, String lastName, String mobileNumber) {
    }

    private final OAuthService        oauth;
    private final SessionService      sessions;
    private final AuthDao             dao;
    private final NovelKmsConfig.Auth config;

    @Inject
    public AuthResource(OAuthService oauth, SessionService sessions, AuthDao dao, NovelKmsConfig config) {
        this.oauth = oauth;
        this.sessions = sessions;
        this.dao = dao;
        this.config = config.getAuth();
    }

    @GET
    @Path("/providers")
    public Map<String, Boolean> providers() {
        return Map.of("google", configured(config.google), "meta", configured(config.meta));
    }

    @GET
    @Path("/{provider}/start")
    public Response start(@PathParam("provider") String provider, @QueryParam("returnTo") String returnTo) throws Exception {
        return Response.seeOther(oauth.begin(provider, returnTo)).build();
    }

    @GET
    @Path("/{provider}/callback")
    public Response callback(@PathParam("provider") String provider,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @Context HttpServletRequest request) throws Exception {
        if (error != null)
            return Response.seeOther(URI.create(config.frontendBaseUrl + "/login?error=oauth_denied")).build();
        OAuthService.CallbackResult result = oauth.callback(provider, code, state);
        if (result.user() != null) {
            String token = sessions.create(result.user(), request.getRemoteAddr(), request.getHeader("User-Agent"));
            return Response.seeOther(URI.create(config.frontendBaseUrl + result.returnPath()))
                    .cookie(sessionCookie(token)).build();
        }
        return Response.seeOther(URI.create(config.frontendBaseUrl + "/register"))
                .cookie(registrationCookie(result.pendingRegistrationToken())).build();
    }

    @GET
    @Path("/status")
    public Response status(@CookieParam(AuthConstants.SESSION_COOKIE) String sessionToken,
            @CookieParam(AuthConstants.REGISTRATION_COOKIE) String registrationToken) throws Exception {
        var user = sessions.authenticate(sessionToken);
        if (user.isPresent()) {
            var u = user.get();
            return Response.ok(Map.of("state", "AUTHENTICATED", "user", Map.of(
                    "id", u.id(), "displayName", u.displayName(), "emailAddress", u.emailAddress()))).build();
        }
        if (registrationToken != null) {
            var pending = dao.findPendingRegistration(CryptoTokens.sha256(registrationToken));
            if (pending.isPresent()) {
                var p = pending.get().profile();
                return Response.ok(Map.of("state", "REGISTRATION_REQUIRED", "registration", Map.of(
                        "emailAddress", p.email(), "firstName", nullToEmpty(p.firstName()), "lastName", nullToEmpty(p.lastName())))).build();
            }
        }
        return Response.ok(Map.of("state", "UNAUTHENTICATED")).build();
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegistrationRequest body,
            @CookieParam(AuthConstants.REGISTRATION_COOKIE) String registrationToken,
            @Context HttpServletRequest request) throws Exception {
        if (registrationToken == null)
            return Response.status(Response.Status.UNAUTHORIZED).build();
        var pending = dao.findPendingRegistration(CryptoTokens.sha256(registrationToken));
        if (pending.isEmpty())
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "registration_expired")).build();
        if (body.displayName() == null || body.displayName().isBlank() || body.displayName().trim().length() > 200)
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "display_name_required")).build();
        var    user    = dao.register(pending.get(), body.firstName(), body.lastName(), body.displayName(), body.mobileNumber());
        String session = sessions.create(user, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return Response.ok(Map.of("state", "AUTHENTICATED", "user", Map.of("id", user.id(), "displayName", user.displayName(), "emailAddress", user.emailAddress())))
                .cookie(sessionCookie(session), clearCookie(AuthConstants.REGISTRATION_COOKIE)).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(AuthConstants.SESSION_COOKIE) String token) throws Exception {
        sessions.revoke(token);
        return Response.noContent().cookie(clearCookie(AuthConstants.SESSION_COOKIE)).build();
    }

    private NewCookie sessionCookie(String value) {
        return new NewCookie.Builder(AuthConstants.SESSION_COOKIE)
                .value(value)
                .path("/")
                .httpOnly(true)
                .secure(config.secureCookies)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge((int) Duration.ofDays(config.sessionDays).toSeconds())
                .build();
    }

    private NewCookie registrationCookie(String value) {
        return new NewCookie.Builder(AuthConstants.REGISTRATION_COOKIE)
                .value(value)
                .path("/api/auth")
                .httpOnly(true)
                .secure(config.secureCookies)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(1200)
                .build();
    }

    private NewCookie clearCookie(String name) {
        return new NewCookie.Builder(name)
                .value("")
                .path(name.equals(AuthConstants.REGISTRATION_COOKIE) ? "/api/auth" : "/")
                .httpOnly(true)
                .secure(config.secureCookies)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(0)
                .build();
    }

    private static boolean configured(NovelKmsConfig.OAuthProvider p) {
        return p != null && p.clientId != null && !p.clientId.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
