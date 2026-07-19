package com.richardsand.novelkms.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.review.ContentReportDao;
import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.model.AdminAuditLogEntry;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.admin.ContentReportView;
import com.richardsand.novelkms.model.review.ContentReport;
import com.richardsand.novelkms.model.review.HumanReview;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.service.ReviewPublishService;

import jakarta.ws.rs.NotFoundException;

/**
 * The admin moderation service (slice 1F): every mutation audits, removals
 * auto-resolve their reports, and a missing target is a 404.
 */
class AdminModerationServiceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);
    private static final HumanReviewDao    REVIEWS   = new HumanReviewDao(ds);
    private static final ContentReportDao  REPORTS   = new ContentReportDao(ds);
    private static final AdminAuditDao     AUDIT     = new AdminAuditDao(ds);

    private static final ReviewPublishService PUBLISH = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private AdminModerationService service;

    private static final UUID ADMIN    = TEST_USER_ID;
    private static final UUID AUTHOR   = OTHER_USER_ID;
    private static final UUID REVIEWER = THIRD_USER_ID;

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        service = new AdminModerationService(REPORTS, REQUESTS, REVIEWS, PROFILES, AUDIT, createMapper());

        PROFILES.create(AUTHOR,   ReviewProfile.builder().handle("Author_One").displayName("Ann").build());
        PROFILES.create(REVIEWER, ReviewProfile.builder().handle("Reviewer_Two").build());

        Project project = createTestProject(AUTHOR, "Series", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    private ReviewRequest publish(String title) throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, title, null, null);
        UUID sid = sceneDao.create(ch.getId(), "Scene", null).getId();
        sceneDao.saveContent(sid, "<p>One two three four five words here.</p>", 0);
        return PUBLISH.publishChapter(AUTHOR, ch.getId(),
                ReviewRequest.builder().title(title).genre("fantasy").build());
    }

    private UUID snapshotId(UUID requestId) throws SQLException {
        return SNAPSHOTS.findByRequestId(requestId).orElseThrow().getId();
    }

    // =========================================================================
    // Reports
    // =========================================================================

    @Test
    void listReports_resolvesReporterHandle() throws SQLException {
        REPORTS.create(REVIEWER, ContentReportDao.TARGET_REQUEST, UUID.randomUUID(), "SPAM", null);

        List<ContentReportView> views = service.listReports(ContentReportDao.STATUS_OPEN, 50);
        assertEquals(1, views.size());
        assertEquals("Reviewer_Two", views.get(0).reporterHandle());
    }

    @Test
    void resolveReport_setsStatus_andAudits() throws SQLException {
        ContentReport r = REPORTS.create(REVIEWER, ContentReportDao.TARGET_REQUEST,
                UUID.randomUUID(), "OTHER", null);

        ContentReportView view = service.resolveReport(ADMIN, r.getId(), "handled", null);

        assertEquals(ContentReportDao.STATUS_RESOLVED, view.status());
        assertTrue(hasAction(AUDIT.recent(50), AdminModerationService.ACTION_RESOLVE_REPORT));
    }

    @Test
    void dismissReport_setsDismissed() throws SQLException {
        ContentReport r = REPORTS.create(REVIEWER, ContentReportDao.TARGET_PROFILE,
                UUID.randomUUID(), "HATE", null);

        ContentReportView view = service.dismissReport(ADMIN, r.getId(), "not warranted", null);
        assertEquals(ContentReportDao.STATUS_DISMISSED, view.status());
    }

    @Test
    void resolveReport_unknownId_is404() {
        assertThrows(NotFoundException.class,
                () -> service.resolveReport(ADMIN, UUID.randomUUID(), "x", null));
    }

    // =========================================================================
    // Removals auto-resolve their reports
    // =========================================================================

    @Test
    void removeRequest_marksRemoved_autoResolvesReports_andAudits() throws SQLException {
        ReviewRequest req = publish("Chapter A");
        REPORTS.create(REVIEWER, ContentReportDao.TARGET_REQUEST, req.getId(), "SPAM", null);

        ReviewRequest after = service.removeRequest(ADMIN, req.getId(), "off-topic promo", null);

        assertEquals(ReviewRequestDao.STATUS_REMOVED, after.getStatus());
        assertTrue(REPORTS.listByStatus(ContentReportDao.STATUS_OPEN, 50).isEmpty(),
                "the request's open report is auto-resolved on removal");
        assertTrue(hasAction(AUDIT.forTargetUser(AUTHOR, 50),
                AdminModerationService.ACTION_REMOVE_REQUEST));
    }

    @Test
    void removeReview_marksRemoved_autoResolvesReports() throws SQLException {
        ReviewRequest req = publish("Chapter B");
        HumanReview review = REVIEWS.insert(req.getId(), snapshotId(req.getId()), REVIEWER,
                "<p>Feedback.</p>", 1, HumanReviewDao.VISIBILITY_PRIVATE, false);
        REVIEWS.submit(review.getId(), REVIEWER, Instant.now());
        REPORTS.create(AUTHOR, ContentReportDao.TARGET_REVIEW, review.getId(), "HARASSMENT", null);

        HumanReview after = service.removeReview(ADMIN, review.getId(), "abusive", null);

        assertEquals(HumanReviewDao.STATUS_REMOVED, after.getStatus());
        assertTrue(REPORTS.listByStatus(ContentReportDao.STATUS_OPEN, 50).isEmpty());
        assertTrue(hasAction(AUDIT.forTargetUser(REVIEWER, 50),
                AdminModerationService.ACTION_REMOVE_REVIEW));
    }

    // =========================================================================
    // Profile suspension
    // =========================================================================

    @Test
    void suspendProfile_setsSuspended_autoResolvesReports_reinstateRestores() throws SQLException {
        ReviewProfile profile = PROFILES.findByHandle("Author_One").orElseThrow();
        REPORTS.create(REVIEWER, ContentReportDao.TARGET_PROFILE, profile.getId(), "HATE", null);

        ReviewProfile suspended = service.suspendProfile(ADMIN, "Author_One", "abuse", null);
        assertEquals(ReviewProfileDao.STATUS_SUSPENDED, suspended.getStatus());
        assertTrue(REPORTS.listByStatus(ContentReportDao.STATUS_OPEN, 50).isEmpty());
        assertTrue(hasAction(AUDIT.forTargetUser(AUTHOR, 50),
                AdminModerationService.ACTION_SUSPEND_PROFILE));

        ReviewProfile reinstated = service.reinstateProfile(ADMIN, "Author_One", "appeal upheld", null);
        assertEquals(ReviewProfileDao.STATUS_ACTIVE, reinstated.getStatus());
    }

    @Test
    void suspendProfile_unknownHandle_is404() {
        assertThrows(NotFoundException.class,
                () -> service.suspendProfile(ADMIN, "Nobody_Here", "x", null));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static boolean hasAction(List<AdminAuditLogEntry> entries, String action) {
        return entries.stream().anyMatch(e -> action.equals(e.action()));
    }
}
