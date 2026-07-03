package com.richardsand.novelkms.resource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.auth.AuthConstants;
import com.richardsand.novelkms.auth.CryptoTokens;
import com.richardsand.novelkms.auth.OAuthService;
import com.richardsand.novelkms.auth.SessionService;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.service.RegistrationNotificationService;
import com.richardsand.novelkms.service.StarterContentService;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    public record RegistrationRequest(
            @NotBlank String displayName,
            String firstName,
            String lastName,
            String mobileNumber) {
    }

    private final OAuthService                    oauth;
    private final SessionService                  sessions;
    private final AuthDao                         dao;
    private final NovelKmsConfig.Auth             config;
    private final RegistrationNotificationService registrationNotifications;
    private final StarterContentService           starterContent;

    @Inject
    public AuthResource(
            OAuthService oauth,
            SessionService sessions,
            AuthDao dao,
            RegistrationNotificationService registrationNotifications,
            StarterContentService starterContent,
            NovelKmsConfig config) {
        this.oauth = oauth;
        this.sessions = sessions;
        this.dao = dao;
        this.registrationNotifications = registrationNotifications;
        this.starterContent = starterContent;
        this.config = config.getAuth();
    }

    @GET
    @Path("/providers")
    public Map<String, Boolean> providers() {
        logger.debug("AuthResource.providers invoked");
        return Map.of(
                "google", configured(config.google),
                "github", configured(config.github),
                "meta", configured(config.meta),
                "microsoft", configured(config.microsoft),
                "apple", configuredApple(config.apple));

    }

    @GET
    @Path("/{provider}/start")
    public Response start(@PathParam("provider") String provider, @QueryParam("returnTo") String returnTo) throws Exception {
        logger.info("OAuth start requested: provider={}, returnTo={}", provider, returnTo);
        return Response.seeOther(oauth.begin(provider, returnTo)).build();
    }

    @GET
    @Path("/{provider}/callback")
    public Response callback(@PathParam("provider") String provider,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @Context HttpServletRequest request) throws Exception {
        return completeCallback(provider, code, state, error, null, request);
    }

    @POST
    @Path("/{provider}/callback")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response callbackPost(@PathParam("provider") String provider,
            @FormParam("code") String code,
            @FormParam("state") String state,
            @FormParam("error") String error,
            @FormParam("user") String user,
            @Context HttpServletRequest request) throws Exception {
        return completeCallback(provider, code, state, error, user, request);
    }

    private Response completeCallback(String provider,
            String code,
            String state,
            String error,
            String providerUserJson,
            HttpServletRequest request) throws Exception {
        logger.info("OAuth callback requested: provider={}, remoteAddr={}", provider, request.getRemoteAddr());
        if (error != null) {
            logger.warn("OAuth callback denied by provider={}: error={}", provider, error);
            return Response.seeOther(URI.create(config.frontendBaseUrl + "/login?error=oauth_denied")).build();
        }
        OAuthService.CallbackResult result = oauth.callback(provider, code, state, providerUserJson);
        if (result.user() != null) {
            logger.info("OAuth callback authenticated existing user: provider={}, userId={}", provider, result.user().id());
            String token = sessions.create(result.user(), request.getRemoteAddr(), request.getHeader("User-Agent"));
            return Response.seeOther(URI.create(config.frontendBaseUrl + result.returnPath()))
                    .cookie(sessionCookie(token)).build();
        }
        logger.info("OAuth callback requires registration: provider={}", provider);
        return Response.seeOther(URI.create(config.frontendBaseUrl + "/register"))
                .cookie(registrationCookie(result.pendingRegistrationToken())).build();
    }

    @GET
    @Path("/status")
    public Response status(@CookieParam(AuthConstants.SESSION_COOKIE) String sessionToken,
            @CookieParam(AuthConstants.REGISTRATION_COOKIE) String registrationToken) throws Exception {
        logger.debug("AuthResource.status invoked: hasSessionCookie={}, hasRegistrationCookie={}", sessionToken != null, registrationToken != null);
        var user = sessions.authenticate(sessionToken);
        if (user.isPresent()) {
            var session = user.get();
            var u       = session.user();

            return Response.ok(Map.of(
                    "state", "AUTHENTICATED",
                    "user", Map.of(
                            "id", u.id(),
                            "displayName", u.displayName(),
                            "emailAddress", u.emailAddress(),
                            "roles", session.roles())))
                    .build();
        }
        if (registrationToken != null) {
            var pending = dao.findPendingRegistration(CryptoTokens.sha256(registrationToken));
            if (pending.isPresent()) {
                var p = pending.get().profile();
                return Response.ok(
                        Map.of(
                                "state",
                                "REGISTRATION_REQUIRED",
                                "registration",
                                Map.of(
                                        "emailAddress",
                                        p.email(),
                                        "firstName",
                                        nullToEmpty(p.firstName()),
                                        "lastName",
                                        nullToEmpty(p.lastName()))))
                        .build();
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

        var     pendingRegistration = pending.get();
        AppUser user                = dao.register(pendingRegistration, body.firstName(), body.lastName(), body.displayName(), body.mobileNumber());
        logger.info("Registration completed: userId={}, email={}", user.id(), user.emailAddress());

        // Seed starter content. Non-fatal: a failure here must not prevent the
        // user from accessing their newly created account.
        try {
            starterContent.seedForNewUser(user);
        } catch (Exception e) {
            logger.warn("Failed to seed starter content for userId={}: {}", user.id(), e.getMessage(), e);
        }

        registrationNotifications.notifyRegistration(user, pendingRegistration, request.getRemoteAddr(), request.getHeader("User-Agent"));

        String session = sessions.create(user, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return Response.ok(
                Map.of(
                        "state",
                        "AUTHENTICATED",
                        "user",
                        Map.of("id", user.id(), "displayName", user.displayName(), "emailAddress", user.emailAddress())))
                .cookie(sessionCookie(session), clearCookie(AuthConstants.REGISTRATION_COOKIE))
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(AuthConstants.SESSION_COOKIE) String token) throws Exception {
        logger.info("Logout requested: hasSessionCookie={}", token != null);
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

    private static boolean configuredApple(NovelKmsConfig.OAuthProvider p) {
        return configured(p)
                && p.teamId != null && !p.teamId.isBlank()
                && p.keyId != null && !p.keyId.isBlank()
                && p.privateKey != null && !p.privateKey.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
