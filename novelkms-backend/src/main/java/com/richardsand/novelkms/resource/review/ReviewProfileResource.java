package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.review.ReviewMetricsDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.model.review.ProfileMetrics;
import com.richardsand.novelkms.model.review.ReviewProfile;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Public identity for the human-review network ({@code review_profile}).
 *
 * <ul>
 *   <li>{@code GET  /review/profile/me}                  — the caller's profile, or 204 if they have none</li>
 *   <li>{@code POST /review/profile}                     — claim a handle and create it</li>
 *   <li>{@code PUT  /review/profile}                     — update the caller's own profile</li>
 *   <li>{@code GET  /review/handles/{handle}/available}  — live availability check for the claim form</li>
 *   <li>{@code GET  /review/profiles/{handle}}           — another user's public profile</li>
 *   <li>{@code GET  /review/profile/metrics}             — the caller's own contribution figures</li>
 *   <li>{@code GET  /review/profiles/{handle}/metrics}   — another user's contribution figures</li>
 * </ul>
 *
 * <p><b>Authorization.</b> These paths carry no manuscript UUID, so
 * {@code TenantAuthorizationFilter} — whose segment switch falls through to
 * {@code default -> true} for unknown collections — does not and cannot guard
 * them. Ownership is enforced here instead: every mutating call is scoped to
 * {@link CurrentUser#id}, and the DAO's update statements carry a
 * {@code WHERE user_id = ?} of their own, so a forged {@code id} in the payload is
 * inert. {@code SubscriptionAuthorizationFilter} still applies, which is the
 * intended Phase 1 policy: participating in the review network requires an active
 * subscription.
 *
 * <p><b>The request context is a method parameter, never a field.</b> Jersey does
 * not proxy {@code ContainerRequestContext} into the fields of a <em>singleton</em>
 * resource. In production that is invisible, because the resource is registered by
 * class and instantiated per request, so a field would bind fine. But
 * {@code ResourceExtension} registers a resource <em>instance</em> — a singleton —
 * and there the field silently stays unbound, so every endpoint NPEs and returns
 * 500. Taking the context as a parameter works identically in both, which is why
 * every resource here that has tests does it this way.
 *
 * <p><b>Status codes.</b> A malformed handle is a client mistake (400); a
 * well-formed handle that cannot be had is a conflict with the state of the world
 * (409). "You have no profile yet" is 400, per the house convention that 404 is
 * reserved for config/routing errors.
 *
 * <p>The one deliberate exception is {@code /review/profiles/{handle}}, the
 * network's first legitimate cross-user read: it returns <b>404</b> — never 403 —
 * for a handle that does not exist, is HIDDEN, or has been SUSPENDED, so profile
 * existence is not disclosed. That matches what {@code TenantAuthorizationFilter}
 * already does for manuscript entities. The caller always gets to read their own
 * profile through this path regardless of visibility, so a hidden profile can still
 * be previewed by its owner.
 */
@Path("/review")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewProfileResource {

    /**
     * Must start with a letter, then letters/digits/underscore, 3–24 characters.
     * Leading digits are excluded so a handle can never be confused with an id, and
     * the character class is kept narrow enough to be unambiguous in a URL and to
     * leave no room for homoglyph games in a first release.
     */
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{2,23}$");

    /**
     * Handles that would collide with a route, impersonate the operator, or imply
     * authority. Compared against the normalized (lowercase) handle.
     */
    private static final Set<String> RESERVED_HANDLES = Set.of(
            "admin", "administrator", "moderator", "mod", "staff", "support", "help",
            "novelkms", "official", "system", "root", "api", "app", "www",
            "settings", "profile", "profiles", "review", "reviews", "queue",
            "me", "you", "new", "null", "undefined", "anonymous", "deleted");

    private static final int MAX_DISPLAY_NAME = 120;
    private static final int MAX_BIO          = 2000;
    private static final int MAX_GENRES       = 12;
    private static final int MAX_GENRE_LENGTH = 40;

    private final ReviewProfileDao dao;
    private final ReviewMetricsDao metricsDao;

    @Inject
    public ReviewProfileResource(ReviewProfileDao dao, ReviewMetricsDao metricsDao) {
        this.dao = dao;
        this.metricsDao = metricsDao;
    }

    /** Create/update payload. Deliberately has no {@code status} — suspension is an admin action. */
    public static class ProfileRequest {
        @JsonProperty
        public String handle;

        @JsonProperty
        public String displayName;

        @JsonProperty
        public String bio;

        @JsonProperty
        public List<String> genresWritten;

        @JsonProperty
        public List<String> genresReviewed;

        @JsonProperty
        public String visibility;
    }

    // =========================================================================
    // The caller's own profile
    // =========================================================================

    @GET
    @Path("/profile/me")
    public Response me(@Context ContainerRequestContext request) {
        return run(() -> dao.findByUserId(CurrentUser.id(request))
                .map(p -> Response.ok(p).build())
                .orElseGet(() -> Response.noContent().build()));
    }

    /**
     * The caller's own contribution figures (§13). Unlike {@link #me}, a caller with
     * no profile is a 404 {@code no_profile} rather than 204: metrics presuppose a
     * profile, and there is nothing meaningful to report without one.
     */
    @GET
    @Path("/profile/metrics")
    public Response metricsMe(@Context ContainerRequestContext request) {
        return run(() -> {
            Optional<ReviewProfile> profile = dao.findByUserId(CurrentUser.id(request));
            if (profile.isEmpty()) {
                return noProfile();
            }
            return okMetrics(profile.get());
        });
    }

    @POST
    @Path("/profile")
    public Response create(ProfileRequest body, @Context ContainerRequestContext request) {
        return run(() -> {
            UUID userId = CurrentUser.id(request);

            if (dao.findByUserId(userId).isPresent()) {
                return error(Response.Status.CONFLICT, "profile_exists",
                        "You already have a reviewer profile.");
            }

            Response invalid = validate(body);
            if (invalid != null) {
                return invalid;
            }

            String reason = handleUnavailableReason(body.handle, null);
            if (reason != null) {
                return error(statusFor(reason), reason, handleMessage(reason));
            }

            try {
                return Response.ok(dao.create(userId, toModel(body))).build();
            } catch (SQLException e) {
                // The unique index on handle_lower is the real arbiter. If two users
                // claim the same handle at once, the loser lands here — report it as
                // the conflict it is rather than a 500.
                if (dao.handleTaken(body.handle, userId)) {
                    return error(Response.Status.CONFLICT, "handle_taken", handleMessage("handle_taken"));
                }
                throw e;
            }
        });
    }

    @PUT
    @Path("/profile")
    public Response update(ProfileRequest body, @Context ContainerRequestContext request) {
        return run(() -> {
            UUID userId = CurrentUser.id(request);

            if (dao.findByUserId(userId).isEmpty()) {
                return noProfile();
            }

            Response invalid = validate(body);
            if (invalid != null) {
                return invalid;
            }

            String reason = handleUnavailableReason(body.handle, userId);
            if (reason != null) {
                return error(statusFor(reason), reason, handleMessage(reason));
            }

            try {
                return dao.update(userId, toModel(body))
                        .map(p -> Response.ok(p).build())
                        .orElseGet(ReviewProfileResource::noProfile);
            } catch (SQLException e) {
                if (dao.handleTaken(body.handle, userId)) {
                    return error(Response.Status.CONFLICT, "handle_taken", handleMessage("handle_taken"));
                }
                throw e;
            }
        });
    }

    // =========================================================================
    // Handle availability
    // =========================================================================

    /**
     * Drives the live check in the claim form. Excludes the caller's own current
     * handle, so re-saving an unchanged profile never reports a conflict against
     * itself.
     *
     * <p>A malformed handle is reported here as unavailable-with-a-reason, NOT as a
     * 400. This endpoint is a question, not a claim — the form asks it on every
     * keystroke, and a stream of 400s would be noise.
     */
    @GET
    @Path("/handles/{handle}/available")
    public Response available(@PathParam("handle") String handle,
            @Context ContainerRequestContext request) {

        return run(() -> {
            String reason = handleUnavailableReason(handle, CurrentUser.id(request));
            return Response.ok(Map.of(
                    "handle", handle == null ? "" : handle,
                    "available", reason == null,
                    "reason", reason == null ? "" : reason,
                    "message", reason == null ? "" : handleMessage(reason))).build();
        });
    }

    // =========================================================================
    // Another user's public profile
    // =========================================================================

    @GET
    @Path("/profiles/{handle}")
    public Response byHandle(@PathParam("handle") String handle,
            @Context ContainerRequestContext request) {

        return run(() -> readableByHandle(handle, request)
                .map(p -> Response.ok(p).build())
                .orElseGet(ReviewProfileResource::notFound));
    }

    /**
     * Another user's contribution figures (§13). Rides the exact same
     * non-disclosing gate as {@link #byHandle}: an absent, HIDDEN, or SUSPENDED
     * handle reads as 404, never 403, and the owner always reaches their own row.
     * Sharing {@link #readableByHandle} keeps the profile view and its metrics from
     * ever disagreeing about who may be seen — the place a 1F block rule would slot
     * in once.
     */
    @GET
    @Path("/profiles/{handle}/metrics")
    public Response metricsByHandle(@PathParam("handle") String handle,
            @Context ContainerRequestContext request) {

        return run(() -> {
            Optional<ReviewProfile> profile = readableByHandle(handle, request);
            if (profile.isEmpty()) {
                return notFound();
            }
            return okMetrics(profile.get());
        });
    }

    /**
     * Resolves a handle to a profile the caller is permitted to see, or empty when
     * it should read as absent — missing, HIDDEN, or SUSPENDED. The owner always
     * sees their own row regardless of visibility so a hidden profile can still be
     * previewed by its owner. This is the network's first legitimate cross-user
     * read gate; both {@link #byHandle} and {@link #metricsByHandle} go through it so
     * the two paths cannot drift.
     */
    private Optional<ReviewProfile> readableByHandle(String handle, ContainerRequestContext request)
            throws SQLException {

        Optional<ReviewProfile> found = dao.findByHandle(handle);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ReviewProfile profile = found.get();
        boolean       isOwner = profile.getUserId().equals(CurrentUser.id(request));

        if (!isOwner
                && (ReviewProfileDao.VISIBILITY_HIDDEN.equals(profile.getVisibility())
                        || ReviewProfileDao.STATUS_SUSPENDED.equals(profile.getStatus()))) {
            return Optional.empty();
        }
        return Optional.of(profile);
    }

    /**
     * Assembles the wire-facing {@link ProfileMetrics} from the derived aggregates
     * and the profile already in hand ({@code handle}, {@code memberSince}). The DAO
     * call throws {@link SQLException}, so every caller invokes this inside a
     * {@link #run} lambda rather than through {@code Optional.map}.
     */
    private Response okMetrics(ReviewProfile profile) throws SQLException {
        ReviewMetricsDao.Contribution c = metricsDao.contributionFor(profile.getUserId());
        ProfileMetrics metrics = ProfileMetrics.builder()
                .handle(profile.getHandle())
                .wordsReviewed(c.wordsReviewed())
                .reviewWordsWritten(c.reviewWordsWritten())
                .reviewsCompleted(c.reviewsCompleted())
                .reviewsReceived(c.reviewsReceived())
                .memberSince(profile.getCreatedAt())
                .build();
        return Response.ok(metrics).build();
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /** Returns a 400 response when the payload is unusable, or null when it is fine. */
    private Response validate(ProfileRequest body) {
        if (body == null) {
            return error(Response.Status.BAD_REQUEST, "missing_body", "A profile payload is required.");
        }
        if (body.displayName != null && body.displayName.trim().length() > MAX_DISPLAY_NAME) {
            return error(Response.Status.BAD_REQUEST, "display_name_too_long",
                    "Display name must be " + MAX_DISPLAY_NAME + " characters or fewer.");
        }
        if (body.bio != null && body.bio.trim().length() > MAX_BIO) {
            return error(Response.Status.BAD_REQUEST, "bio_too_long",
                    "Bio must be " + MAX_BIO + " characters or fewer.");
        }

        Response genreError = validateGenres(body.genresWritten, "genresWritten");
        if (genreError != null) {
            return genreError;
        }
        return validateGenres(body.genresReviewed, "genresReviewed");
    }

    private Response validateGenres(List<String> genres, String field) {
        if (genres == null) {
            return null;
        }
        if (genres.size() > MAX_GENRES) {
            return error(Response.Status.BAD_REQUEST, "too_many_genres",
                    field + " may list at most " + MAX_GENRES + " genres.");
        }
        for (String genre : genres) {
            if (genre != null && genre.trim().length() > MAX_GENRE_LENGTH) {
                return error(Response.Status.BAD_REQUEST, "genre_too_long",
                        "Each genre must be " + MAX_GENRE_LENGTH + " characters or fewer.");
            }
            if (genre != null && genre.contains(",")) {
                // Genres are packed into one comma-separated column; a comma inside a
                // value would silently split it into two on the way back out.
                return error(Response.Status.BAD_REQUEST, "genre_invalid",
                        "A genre may not contain a comma.");
            }
        }
        return null;
    }

    /**
     * The single source of truth for "can this handle be claimed" — shared by
     * create, update, and the live availability check, so the form and the write
     * path can never disagree.
     *
     * @return a machine-readable reason, or null when the handle is available
     */
    private String handleUnavailableReason(String handle, UUID excludeUserId) throws SQLException {
        String trimmed = handle == null ? "" : handle.trim();

        if (trimmed.isEmpty()) {
            return "handle_required";
        }
        if (!HANDLE_PATTERN.matcher(trimmed).matches()) {
            return "handle_invalid";
        }
        if (RESERVED_HANDLES.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return "handle_reserved";
        }
        if (dao.handleTaken(trimmed, excludeUserId)) {
            return "handle_taken";
        }
        return null;
    }

    /**
     * A malformed handle is a client mistake (400); a handle that is well-formed but
     * cannot be had is a conflict with the current state of the world (409).
     * Collapsing both into 409 — as the first cut of this class did — tells the
     * caller "try a different handle" when the real answer is "that isn't a handle."
     */
    private static Response.Status statusFor(String reason) {
        return switch (reason) {
        case "handle_required", "handle_invalid" -> Response.Status.BAD_REQUEST;
        default                                  -> Response.Status.CONFLICT;
        };
    }

    private static String handleMessage(String reason) {
        return switch (reason) {
        case "handle_required" -> "Choose a handle.";
        case "handle_invalid"  -> "Handles are 3–24 characters, start with a letter, "
                + "and use only letters, numbers, and underscores.";
        case "handle_reserved" -> "That handle is reserved.";
        case "handle_taken"    -> "That handle is already taken.";
        default                -> "That handle cannot be used.";
        };
    }

    private static ReviewProfile toModel(ProfileRequest body) {
        return ReviewProfile.builder()
                .handle(body.handle)
                .displayName(body.displayName)
                .bio(body.bio)
                .genresWritten(body.genresWritten)
                .genresReviewed(body.genresReviewed)
                .visibility(body.visibility)
                .build();
    }

    // =========================================================================
    // Response helpers
    // =========================================================================

    /**
     * Not a 404. House convention reserves 404 for config/routing errors, and
     * AccountResource already answers 400 for a missing entity. The only 404s in
     * this class are the deliberate non-disclosure ones in {@link #byHandle}.
     */
    /**
     * The caller has no reviewer profile yet.
     *
     * <p>404, not 400: the request is perfectly well formed, the resource simply
     * does not exist. This also keeps the review network's one status convention
     * intact — a profile you cannot see reads as absent, never as refused.
     */
    private static Response noProfile() {
        return error(Response.Status.NOT_FOUND, "no_profile",
                "You do not have a reviewer profile yet.");
    }

    private static Response notFound() {
        return error(Response.Status.NOT_FOUND, "not_found", "No such profile.");
    }

    private static Response error(Response.Status status, String code, String message) {
        return Response.status(status)
                .entity(Map.of("error", code, "message", message))
                .build();
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", "server_error",
                    "message", String.valueOf(e.getMessage()))).build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
