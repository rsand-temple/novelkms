package com.richardsand.novelkms.service.admin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.review.ContentReportDao;
import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.model.admin.ContentReportView;
import com.richardsand.novelkms.model.review.ContentReport;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;

import jakarta.ws.rs.NotFoundException;

/**
 * The admin moderation surface for the human-review network (slice 1F): triaging
 * content reports, removing offending requests and reviews, and suspending or
 * reinstating profiles.
 *
 * <p><b>Every mutation is audited.</b> This service is the review-network peer of
 * {@code AdminBillingService}: it captures the prior state, performs the mutation
 * through the owning DAO's <em>admin</em> setter (deliberately not author/reviewer
 * scoped — the guard is {@code @RolesAllowed(ADMIN)} on the resource, not a WHERE
 * clause), and writes an {@code admin_audit_log} row with old/new JSON and the
 * admin's reason. A moderation action that touches a specific user stamps that
 * user as the audit target so support can trace everything done to an account.
 *
 * <p><b>Removal auto-resolves its reports.</b> Removing a request or review, or
 * suspending a profile, closes every still-OPEN report pointing at that target
 * (spec: the moderation action that a report prompted should close the report),
 * so the queue never accumulates orphan OPEN reports about content that is already
 * gone.
 *
 * <p>A missing target is a {@link NotFoundException} (404), letting JAX-RS map it
 * the same way the other admin resources do.
 */
public class AdminModerationService {

    public static final String ACTION_REMOVE_REQUEST     = "REMOVE_REVIEW_REQUEST";
    public static final String ACTION_REMOVE_REVIEW      = "REMOVE_HUMAN_REVIEW";
    public static final String ACTION_SUSPEND_PROFILE    = "SUSPEND_REVIEW_PROFILE";
    public static final String ACTION_REINSTATE_PROFILE  = "REINSTATE_REVIEW_PROFILE";
    public static final String ACTION_RESOLVE_REPORT     = "RESOLVE_CONTENT_REPORT";
    public static final String ACTION_DISMISS_REPORT     = "DISMISS_CONTENT_REPORT";

    public static final String ENTITY_REVIEW_REQUEST = "review_request";
    public static final String ENTITY_HUMAN_REVIEW   = "human_review";
    public static final String ENTITY_REVIEW_PROFILE = "review_profile";
    public static final String ENTITY_CONTENT_REPORT = "content_report";

    private final ContentReportDao contentReportDao;
    private final ReviewRequestDao reviewRequestDao;
    private final HumanReviewDao   humanReviewDao;
    private final ReviewProfileDao reviewProfileDao;
    private final AdminAuditDao    adminAuditDao;
    private final ObjectMapper     objectMapper;

    public AdminModerationService(
            ContentReportDao contentReportDao,
            ReviewRequestDao reviewRequestDao,
            HumanReviewDao humanReviewDao,
            ReviewProfileDao reviewProfileDao,
            AdminAuditDao adminAuditDao,
            ObjectMapper objectMapper) {

        this.contentReportDao = contentReportDao;
        this.reviewRequestDao = reviewRequestDao;
        this.humanReviewDao = humanReviewDao;
        this.reviewProfileDao = reviewProfileDao;
        this.adminAuditDao = adminAuditDao;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Reports
    // =========================================================================

    /**
     * The report queue for the admin console, newest first, with each reporter
     * resolved to their handle. Reporter handles are batch-resolved in one query
     * rather than per row.
     */
    public List<ContentReportView> listReports(String status, Integer limit) throws SQLException {
        List<ContentReport> reports = contentReportDao.listByStatus(status, limit);
        if (reports.isEmpty()) {
            return List.of();
        }

        List<UUID> reporterIds = new ArrayList<>();
        for (ContentReport r : reports) {
            if (r.getReporterUserId() != null) {
                reporterIds.add(r.getReporterUserId());
            }
        }

        Map<UUID, String> handleByUserId = new HashMap<>();
        for (ReviewProfile p : reviewProfileDao.findByUserIds(reporterIds)) {
            handleByUserId.put(p.getUserId(), p.getHandle());
        }

        List<ContentReportView> out = new ArrayList<>(reports.size());
        for (ContentReport r : reports) {
            out.add(view(r, handleByUserId.get(r.getReporterUserId())));
        }
        return out;
    }

    /** Marks a report RESOLVED. */
    public ContentReportView resolveReport(UUID adminUserId, UUID reportId, String reason, String note)
            throws SQLException {
        return closeReport(adminUserId, reportId, ContentReportDao.STATUS_RESOLVED,
                ACTION_RESOLVE_REPORT, reason, note);
    }

    /** Marks a report DISMISSED (no action warranted). */
    public ContentReportView dismissReport(UUID adminUserId, UUID reportId, String reason, String note)
            throws SQLException {
        return closeReport(adminUserId, reportId, ContentReportDao.STATUS_DISMISSED,
                ACTION_DISMISS_REPORT, reason, note);
    }

    private ContentReportView closeReport(UUID adminUserId, UUID reportId, String status,
            String action, String reason, String note) throws SQLException {

        ContentReport before = contentReportDao.findById(reportId)
                .orElseThrow(() -> new NotFoundException("No such report"));

        ContentReport after = contentReportDao.resolve(reportId, adminUserId, status, note(reason, note))
                .orElseThrow(() -> new NotFoundException("No such report"));

        adminAuditDao.record(adminUserId, null, action, ENTITY_CONTENT_REPORT,
                reportId.toString(), toJson(before), toJson(after), reason(reason, note));

        return view(after, reporterHandle(after));
    }

    // =========================================================================
    // Removals
    // =========================================================================

    /** Removes a review request (→ REMOVED) and auto-resolves its OPEN reports. */
    public ReviewRequest removeRequest(UUID adminUserId, UUID requestId, String reason, String note)
            throws SQLException {

        ReviewRequest before = reviewRequestDao.findById(requestId)
                .orElseThrow(() -> new NotFoundException("No such review request"));

        ReviewRequest after = reviewRequestDao.adminSetStatus(requestId, ReviewRequestDao.STATUS_REMOVED)
                .orElseThrow(() -> new NotFoundException("No such review request"));

        adminAuditDao.record(adminUserId, before.getAuthorUserId(), ACTION_REMOVE_REQUEST,
                ENTITY_REVIEW_REQUEST, requestId.toString(), toJson(before), toJson(after),
                reason(reason, note));

        contentReportDao.resolveOpenForTarget(ContentReportDao.TARGET_REQUEST, requestId,
                adminUserId, autoResolveNote(ACTION_REMOVE_REQUEST));

        return after;
    }

    /** Removes a human review (→ REMOVED) and auto-resolves its OPEN reports. */
    public HumanReview removeReview(UUID adminUserId, UUID reviewId, String reason, String note)
            throws SQLException {

        HumanReview before = humanReviewDao.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("No such review"));

        HumanReview after = humanReviewDao.adminSetStatus(reviewId, HumanReviewDao.STATUS_REMOVED)
                .orElseThrow(() -> new NotFoundException("No such review"));

        adminAuditDao.record(adminUserId, before.getReviewerUserId(), ACTION_REMOVE_REVIEW,
                ENTITY_HUMAN_REVIEW, reviewId.toString(), toJson(before), toJson(after),
                reason(reason, note));

        contentReportDao.resolveOpenForTarget(ContentReportDao.TARGET_REVIEW, reviewId,
                adminUserId, autoResolveNote(ACTION_REMOVE_REVIEW));

        return after;
    }

    // =========================================================================
    // Profile suspension
    // =========================================================================

    /** Suspends a profile by handle and auto-resolves its OPEN reports. */
    public ReviewProfile suspendProfile(UUID adminUserId, String handle, String reason, String note)
            throws SQLException {
        return setProfileStatus(adminUserId, handle, ReviewProfileDao.STATUS_SUSPENDED,
                ACTION_SUSPEND_PROFILE, reason, note, true);
    }

    /** Reinstates a suspended profile by handle. Does not touch reports. */
    public ReviewProfile reinstateProfile(UUID adminUserId, String handle, String reason, String note)
            throws SQLException {
        return setProfileStatus(adminUserId, handle, ReviewProfileDao.STATUS_ACTIVE,
                ACTION_REINSTATE_PROFILE, reason, note, false);
    }

    private ReviewProfile setProfileStatus(UUID adminUserId, String handle, String status,
            String action, String reason, String note, boolean autoResolveReports) throws SQLException {

        ReviewProfile before = reviewProfileDao.findByHandle(handle)
                .orElseThrow(() -> new NotFoundException("No such profile"));

        reviewProfileDao.setStatus(before.getUserId(), status);

        ReviewProfile after = reviewProfileDao.findByUserId(before.getUserId())
                .orElseThrow(() -> new NotFoundException("No such profile"));

        adminAuditDao.record(adminUserId, before.getUserId(), action, ENTITY_REVIEW_PROFILE,
                before.getId().toString(), toJson(before), toJson(after), reason(reason, note));

        if (autoResolveReports) {
            contentReportDao.resolveOpenForTarget(ContentReportDao.TARGET_PROFILE, before.getId(),
                    adminUserId, autoResolveNote(action));
        }

        return after;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ContentReportView view(ContentReport r, String reporterHandle) {
        return new ContentReportView(
                r.getId(),
                reporterHandle,
                r.getTargetType(),
                r.getTargetId(),
                r.getReason(),
                r.getDetail(),
                r.getStatus(),
                r.getResolutionNote(),
                r.getCreatedAt(),
                r.getResolvedAt());
    }

    private String reporterHandle(ContentReport r) throws SQLException {
        if (r.getReporterUserId() == null) {
            return null;
        }
        return reviewProfileDao.findByUserId(r.getReporterUserId())
                .map(ReviewProfile::getHandle)
                .orElse(null);
    }

    /** The reason recorded on the audit row; the note is a longer optional elaboration. */
    private static String reason(String reason, String note) {
        if (reason != null && !reason.isBlank()) {
            return note == null || note.isBlank() ? reason.trim() : reason.trim() + " — " + note.trim();
        }
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** The note stored on the report row itself. */
    private static String note(String reason, String note) {
        return reason(reason, note);
    }

    private static String autoResolveNote(String action) {
        return "Auto-resolved by moderation action: " + action;
    }

    private String toJson(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // The audit trail must never block the mutation it records; fall back to a
            // marker rather than failing the whole action over a serialization hiccup.
            return "\"<unserializable: " + value.getClass().getSimpleName() + ">\"";
        }
    }
}
