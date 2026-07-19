package com.richardsand.novelkms.resource.review;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.review.ContentReportDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.model.review.BlockedUser;
import com.richardsand.novelkms.model.review.ContentReport;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.service.ReviewException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The user-facing safety surface of the human-review network (slice 1F): blocking
 * other participants and reporting content for moderation.
 *
 * <ul>
 *   <li>{@code GET    /review/blocks}            — the caller's own block list</li>
 *   <li>{@code POST   /review/blocks}            — block a user by handle</li>
 *   <li>{@code DELETE /review/blocks/{handle}}   — unblock</li>
 *   <li>{@code POST   /review/reports}           — file a moderation report</li>
 * </ul>
 *
 * <p>Another {@code @Path("/review")} class alongside the profile, queue, request,
 * and human-review resources — Jersey owns them side by side because their method
 * sub-paths ({@code /blocks}, {@code /reports}) collide with nothing else. As with
 * the rest of the network, {@code TenantAuthorizationFilter} falls through to
 * {@code default -> true} here, so authorization is explicit: every action is gated
 * on the caller holding an ACTIVE review profile (a handle is the gate for all
 * participation, §14), and {@code SubscriptionAuthorizationFilter} still applies.
 *
 * <p>Everything is done by <b>handle</b>, never by user id — blocking resolves a
 * handle to a user server-side and never echoes the id back. A denied cross-user
 * read (a handle that does not resolve) is a 404, matching the network's
 * non-disclosure convention.
 *
 * <p>The request context is a method parameter, never a field, so the resource
 * behaves identically per-request (production) and as a singleton
 * ({@code ResourceExtension} in tests).
 */
@Path("/review")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewSafetyResource {

    /** Fixed report reasons. UPPER_SNAKE keys; the UI owns the human labels. */
    private static final Set<String> REPORT_REASONS = Set.of(
            "SPAM", "HARASSMENT", "COPYRIGHT", "HATE", "EXPLICIT", "OTHER");

    private static final int MAX_DETAIL = 2000;

    private final UserBlockDao      blockDao;
    private final ReviewProfileDao  profileDao;
    private final ContentReportDao  reportDao;

    @Inject
    public ReviewSafetyResource(UserBlockDao blockDao, ReviewProfileDao profileDao,
            ContentReportDao reportDao) {
        this.blockDao = blockDao;
        this.profileDao = profileDao;
        this.reportDao = reportDao;
    }

    /** Block payload: the handle of the user to block. */
    public static class BlockRequest {
        @JsonProperty
        public String handle;
    }

    /**
     * Report payload. {@code targetType} is REQUEST, REVIEW, or PROFILE. For REQUEST
     * and REVIEW the target is a {@code targetId} the caller already has (the request
     * or review id shown on the card). For PROFILE the caller supplies a
     * {@code targetHandle}, which is resolved to the profile id server-side so no
     * profile id needs to travel on the wire.
     */
    public static class ReportRequest {
        @JsonProperty
        public String targetType;

        @JsonProperty
        public UUID targetId;

        @JsonProperty
        public String targetHandle;

        @JsonProperty
        public String reason;

        @JsonProperty
        public String detail;
    }

    // =========================================================================
    // Blocking
    // =========================================================================

    @GET
    @Path("/blocks")
    public Response listBlocks(@Context ContainerRequestContext request) {
        return run(() -> {
            UUID me = CurrentUser.id(request);
            requireActiveProfile(me);
            List<BlockedUser> blocks = blockDao.listBlocked(me);
            return Response.ok(blocks).build();
        });
    }

    @POST
    @Path("/blocks")
    public Response block(BlockRequest body, @Context ContainerRequestContext request) {
        return run(() -> {
            UUID me = CurrentUser.id(request);
            requireActiveProfile(me);

            if (body == null || body.handle == null || body.handle.isBlank()) {
                return error(Response.Status.BAD_REQUEST, "handle_required",
                        "A handle to block is required.");
            }

            ReviewProfile target = profileDao.findByHandle(body.handle)
                    .orElseThrow(ReviewSafetyResource::notFound);

            if (target.getUserId().equals(me)) {
                return error(Response.Status.BAD_REQUEST, "cannot_block_self",
                        "You cannot block yourself.");
            }

            blockDao.block(me, target.getUserId());
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/blocks/{handle}")
    public Response unblock(@PathParam("handle") String handle,
            @Context ContainerRequestContext request) {

        return run(() -> {
            UUID me = CurrentUser.id(request);
            requireActiveProfile(me);

            // Idempotent: if the handle no longer resolves to a profile there is
            // nothing to unblock, and the caller's intent (not blocking them) holds.
            Optional<ReviewProfile> target = profileDao.findByHandle(handle);
            if (target.isPresent()) {
                blockDao.unblock(me, target.get().getUserId());
            }
            return Response.noContent().build();
        });
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    @POST
    @Path("/reports")
    public Response report(ReportRequest body, @Context ContainerRequestContext request) {
        return run(() -> {
            UUID me = CurrentUser.id(request);
            requireActiveProfile(me);

            if (body == null || body.targetType == null || body.targetType.isBlank()) {
                return error(Response.Status.BAD_REQUEST, "target_required",
                        "A report target is required.");
            }

            String type = body.targetType.trim().toUpperCase(Locale.ROOT);
            String reason = body.reason == null ? "" : body.reason.trim().toUpperCase(Locale.ROOT);
            if (!REPORT_REASONS.contains(reason)) {
                return error(Response.Status.BAD_REQUEST, "reason_invalid",
                        "A valid report reason is required.");
            }
            if (body.detail != null && body.detail.trim().length() > MAX_DETAIL) {
                return error(Response.Status.BAD_REQUEST, "detail_too_long",
                        "Detail must be " + MAX_DETAIL + " characters or fewer.");
            }

            UUID targetId = resolveTarget(type, body);
            if (targetId == null) {
                return error(Response.Status.BAD_REQUEST, "target_invalid",
                        "The report target could not be identified.");
            }

            ContentReport created = reportDao.create(me, type, targetId, reason,
                    blankToNull(body.detail));

            return Response.ok(Map.of(
                    "id", created.getId().toString(),
                    "status", created.getStatus())).build();
        });
    }

    /**
     * Resolves a report to its concrete target UUID. REQUEST/REVIEW carry the id
     * directly; PROFILE is resolved from a handle so profile ids never travel on the
     * wire. An unknown type or a PROFILE handle that does not resolve yields null,
     * which the caller turns into a 400.
     */
    private UUID resolveTarget(String type, ReportRequest body) throws SQLException {
        switch (type) {
        case ContentReportDao.TARGET_REQUEST:
        case ContentReportDao.TARGET_REVIEW:
            return body.targetId;
        case ContentReportDao.TARGET_PROFILE:
            if (body.targetHandle != null && !body.targetHandle.isBlank()) {
                return profileDao.findByHandle(body.targetHandle)
                        .map(ReviewProfile::getId)
                        .orElse(null);
            }
            return body.targetId;
        default:
            return null;
        }
    }

    // =========================================================================
    // Participation gate
    // =========================================================================

    /**
     * Participation requires a handle (§14); a suspended account cannot participate
     * (§21). The suspension answer is about the caller's own account, so 403 is
     * honest — there is nothing to conceal from a user about themselves. Mirrors the
     * gate in {@code ReviewAccessService}.
     */
    private ReviewProfile requireActiveProfile(UUID userId) throws SQLException {
        ReviewProfile profile = profileDao.findByUserId(userId)
                .orElseThrow(() -> new ReviewException(409, "profile_required",
                        "Claim a handle before taking part in the review community."));

        if (ReviewProfileDao.STATUS_SUSPENDED.equals(profile.getStatus())) {
            throw new ReviewException(403, "suspended",
                    "Your review-network access is suspended.");
        }
        return profile;
    }

    // =========================================================================
    // Plumbing
    // =========================================================================

    private static ReviewException notFound() {
        return new ReviewException(404, "not_found", "No such user.");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Response error(Response.Status status, String code, String message) {
        return Response.status(status)
                .entity(Map.of("error", code, "message", message))
                .build();
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (ReviewException e) {
            return Response.status(e.status())
                    .entity(Map.of("error", e.code(), "message", String.valueOf(e.getMessage())))
                    .build();
        } catch (SQLException e) {
            return Response.serverError()
                    .entity(Map.of("error", "server_error", "message", String.valueOf(e.getMessage())))
                    .build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
