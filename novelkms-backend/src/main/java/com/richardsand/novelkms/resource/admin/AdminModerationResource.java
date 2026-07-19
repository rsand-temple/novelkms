package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.model.admin.ContentReportView;
import com.richardsand.novelkms.model.admin.ModerationActionRequest;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.service.admin.AdminModerationService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Admin moderation for the human-review network (slice 1F).
 *
 * <ul>
 *   <li>{@code GET  /admin/moderation/reports?status=&limit=}       — the report queue</li>
 *   <li>{@code POST /admin/moderation/reports/{reportId}/resolve}   — mark a report resolved</li>
 *   <li>{@code POST /admin/moderation/reports/{reportId}/dismiss}   — mark a report dismissed</li>
 *   <li>{@code POST /admin/moderation/requests/{requestId}/remove}  — remove a review request</li>
 *   <li>{@code POST /admin/moderation/reviews/{reviewId}/remove}    — remove a human review</li>
 *   <li>{@code POST /admin/moderation/profiles/{handle}/suspend}    — suspend a profile</li>
 *   <li>{@code POST /admin/moderation/profiles/{handle}/reinstate}  — reinstate a profile</li>
 * </ul>
 *
 * <p>{@code @RolesAllowed(ADMIN)} is the sole authorization gate: the tenant and
 * subscription filters already let {@code /admin/*} through for admin principals,
 * and the DAO setters this reaches are deliberately not owner-scoped. Every action
 * is audited inside {@link AdminModerationService}; the resource only supplies the
 * acting admin id and forwards the reason/note.
 *
 * <p>The request context is a method parameter, never a field, so the resource
 * behaves identically whether Jersey instantiates it per-request (production) or as
 * a singleton ({@code ResourceExtension} in tests).
 */
@Path("/admin/moderation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminModerationResource {

    private final AdminModerationService service;

    @Inject
    public AdminModerationResource(AdminModerationService service) {
        this.service = service;
    }

    // =========================================================================
    // Reports
    // =========================================================================

    @GET
    @Path("/reports")
    public List<ContentReportView> reports(
            @QueryParam("status") @DefaultValue("OPEN") String status,
            @QueryParam("limit") Integer limit) throws SQLException {

        // An explicit empty status means "all"; the default is the OPEN backlog.
        String effective = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null
                : status.trim().toUpperCase();
        return service.listReports(effective, limit);
    }

    @POST
    @Path("/reports/{reportId}/resolve")
    public ContentReportView resolve(
            @Context ContainerRequestContext request,
            @PathParam("reportId") UUID reportId,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.resolveReport(CurrentUser.id(request), reportId, b.reason(), b.note());
    }

    @POST
    @Path("/reports/{reportId}/dismiss")
    public ContentReportView dismiss(
            @Context ContainerRequestContext request,
            @PathParam("reportId") UUID reportId,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.dismissReport(CurrentUser.id(request), reportId, b.reason(), b.note());
    }

    // =========================================================================
    // Removals
    // =========================================================================

    @POST
    @Path("/requests/{requestId}/remove")
    public ReviewRequest removeRequest(
            @Context ContainerRequestContext request,
            @PathParam("requestId") UUID requestId,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.removeRequest(CurrentUser.id(request), requestId, b.reason(), b.note());
    }

    @POST
    @Path("/reviews/{reviewId}/remove")
    public HumanReview removeReview(
            @Context ContainerRequestContext request,
            @PathParam("reviewId") UUID reviewId,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.removeReview(CurrentUser.id(request), reviewId, b.reason(), b.note());
    }

    // =========================================================================
    // Profile suspension
    // =========================================================================

    @POST
    @Path("/profiles/{handle}/suspend")
    public ReviewProfile suspend(
            @Context ContainerRequestContext request,
            @PathParam("handle") String handle,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.suspendProfile(CurrentUser.id(request), handle, b.reason(), b.note());
    }

    @POST
    @Path("/profiles/{handle}/reinstate")
    public ReviewProfile reinstate(
            @Context ContainerRequestContext request,
            @PathParam("handle") String handle,
            ModerationActionRequest body) throws SQLException {

        ModerationActionRequest b = body(body);
        return service.reinstateProfile(CurrentUser.id(request), handle, b.reason(), b.note());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static ModerationActionRequest body(ModerationActionRequest body) {
        return body == null ? new ModerationActionRequest(null, null) : body;
    }
}
