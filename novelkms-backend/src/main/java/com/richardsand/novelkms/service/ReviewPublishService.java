package com.richardsand.novelkms.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewRequestSummary;
import com.richardsand.novelkms.model.review.ReviewSnapshot;
import com.richardsand.novelkms.utils.WordCount;

/**
 * Publishing a chapter for human review, and the lifecycle of what results.
 *
 * <p>Publishing is the moment the review network touches the manuscript, and it is
 * the only one. Everything downstream reads the frozen snapshot this service
 * writes; nothing ever reads back through to the live chapter. That is what lets
 * an author keep editing — or delete the chapter outright — without disturbing a
 * reviewer mid-sentence.
 *
 * <p><b>Snapshot assembly.</b> Scenes in display order, concatenated, separated by
 * a bare {@code <hr>}. Not {@code <hr data-scene-after="{uuid}">}, which is what
 * the editor emits: {@code SceneBreak.js} already parses a bare {@code <hr>} for
 * backward compatibility, and the attribute would hand a stranger the internal
 * UUIDs of the author's scenes for no benefit at all.
 *
 * <p><b>Word count is recomputed, never summed.</b> {@code scene.word_count} was
 * historically zeroed by the pre-V37 autosave path and needed a backfill. A
 * snapshot's count is the number reviewers judge a package by and the number every
 * contribution metric is built from, so it is computed from the assembled HTML at
 * capture time rather than inherited from a column that may be stale.
 */
public class ReviewPublishService {

    /** Scene separator in a snapshot. Deliberately carries no scene id. */
    static final String SCENE_BREAK = "<hr>";

    private final BasicDataSource   ds;
    private final ChapterDao        chapterDao;
    private final SceneDao          sceneDao;
    private final BookDao           bookDao;
    private final ProjectDao        projectDao;
    private final ReviewProfileDao  profileDao;
    private final ReviewRequestDao  requestDao;
    private final ReviewSnapshotDao snapshotDao;

    public ReviewPublishService(BasicDataSource ds,
            ChapterDao chapterDao,
            SceneDao sceneDao,
            BookDao bookDao,
            ProjectDao projectDao,
            ReviewProfileDao profileDao,
            ReviewRequestDao requestDao,
            ReviewSnapshotDao snapshotDao) {
        this.ds = ds;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.bookDao = bookDao;
        this.projectDao = projectDao;
        this.profileDao = profileDao;
        this.requestDao = requestDao;
        this.snapshotDao = snapshotDao;
    }

    // =========================================================================
    // Publish
    // =========================================================================

    /**
     * Freezes a chapter into a snapshot and opens a review request over it, in one
     * transaction.
     *
     * <p>Chapter ownership is NOT checked here: the caller reaches this through
     * {@code POST /chapters/{chapterId}/review-requests}, and
     * {@code TenantAuthorizationFilter} authorizes any UUID that follows a
     * {@code chapters} segment. That is precisely why the endpoint hangs off the
     * manuscript path instead of taking a chapter id in the body — a body-carried
     * id would sail past the filter unchecked.
     */
    public ReviewRequest publishChapter(UUID authorUserId, UUID chapterId, ReviewRequest form)
            throws SQLException {

        // A handle is the gate for all participation. Without one there is nobody
        // for a reviewer to be reviewing for.
        if (profileDao.findByUserId(authorUserId).isEmpty()) {
            throw new ReviewException(409, "profile_required",
                    "Claim a handle before publishing for review.");
        }

        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "No such chapter."));

        // Codex categories are stored as chapter rows with codex_id set and book_id
        // null. They are not manuscript and must never be publishable.
        if (chapter.getCodexId() != null || chapter.getBookId() == null) {
            throw new ReviewException(400, "not_manuscript",
                    "Only manuscript chapters can be published for review.");
        }

        String content = assembleChapterHtml(chapterId);
        if (content.isBlank()) {
            throw new ReviewException(400, "empty_chapter",
                    "This chapter has no text to review yet.");
        }

        Book    book    = bookDao.findById(chapter.getBookId()).orElse(null);
        String  projectTitle = book == null ? null
                : projectDao.findById(book.getProjectId()).map(p -> p.getTitle()).orElse(null);

        Instant now         = Instant.now();
        UUID    requestId   = UUID.randomUUID();
        UUID    snapshotId  = UUID.randomUUID();

        ReviewRequest toInsert = ReviewRequest.builder()
                .sourceScope(ReviewRequestDao.SCOPE_CHAPTER)
                .sourceEntityId(chapterId)
                .title(requireTitle(form, chapter))
                .description(trimToNull(form.getDescription()))
                .authorQuestions(trimToNull(form.getAuthorQuestions()))
                .genre(trimToNull(form.getGenre()))
                .feedbackTypes(form.getFeedbackTypes())
                .contentWarnings(trimToNull(form.getContentWarnings()))
                .visibility(visibilityOrDefault(form.getVisibility()))
                .status(ReviewRequestDao.STATUS_OPEN)
                .maxReviews(positiveOrNull(form.getMaxReviews()))
                .closesAt(form.getClosesAt())
                .build();

        ReviewSnapshot snapshot = ReviewSnapshot.builder()
                .sourceScope(ReviewRequestDao.SCOPE_CHAPTER)
                .sourceEntityId(chapterId)
                .sourceTitle(sourceTitle(chapter))
                .bookTitle(book == null ? null : book.getTitle())
                .projectTitle(projectTitle)
                .contentHtml(content)
                .wordCount(WordCount.fromHtml(content))
                .sourceUpdatedAt(chapter.getUpdatedAt())
                .build();

        // A request without its snapshot is a package with no manuscript — there is
        // nothing sane to display and nothing to recover. Both rows or neither.
        try (Connection c = ds.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                ReviewRequest created = requestDao.insert(c, requestId, authorUserId, toInsert, now);
                snapshotDao.insert(c, snapshotId, requestId, snapshot, now);
                c.commit();
                return created;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    /**
     * Scenes in display order, joined by a bare {@code <hr>}. Blank scenes are
     * dropped rather than emitting an empty block between two breaks — an author
     * with a placeholder scene should not publish a package with a visible hole in
     * it.
     */
    String assembleChapterHtml(UUID chapterId) throws SQLException {
        List<Scene>  scenes = sceneDao.findByChapterId(chapterId);
        List<String> parts  = new ArrayList<>();

        for (Scene scene : scenes) {
            String html = scene.getContent();
            if (html != null && !html.isBlank() && WordCount.fromHtml(html) > 0) {
                parts.add(html.trim());
            }
        }

        return String.join(SCENE_BREAK, parts);
    }

    // =========================================================================
    // Read
    // =========================================================================

    /** The author's own requests, newest first, with snapshot metadata and source state. */
    public List<ReviewRequestSummary> mine(UUID authorUserId) throws SQLException {
        List<ReviewRequest> requests = requestDao.findByAuthor(authorUserId);
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<UUID, ReviewSnapshot> snapshots = snapshotDao.findMetaByRequestIds(
                requests.stream().map(ReviewRequest::getId).toList());

        List<ReviewRequestSummary> out = new ArrayList<>();
        for (ReviewRequest r : requests) {
            ReviewSnapshot s = snapshots.get(r.getId());
            out.add(ReviewRequestSummary.builder()
                    .id(r.getId())
                    .title(r.getTitle())
                    .description(r.getDescription())
                    .genre(r.getGenre())
                    .feedbackTypes(r.getFeedbackTypes())
                    .visibility(r.getVisibility())
                    .status(r.getStatus())
                    .publishedAt(r.getPublishedAt())
                    .closesAt(r.getClosesAt())
                    .closedAt(r.getClosedAt())
                    .updatedAt(r.getUpdatedAt())
                    .sourceTitle(s == null ? null : s.getSourceTitle())
                    .bookTitle(s == null ? null : s.getBookTitle())
                    .wordCount(s == null ? 0 : s.getWordCount())
                    .snapshotCreatedAt(s == null ? null : s.getCreatedAt())
                    .sourceState(sourceState(r, s))
                    .reviewCount(0)
                    .build());
        }
        return out;
    }

    /**
     * Has the chapter this package was cut from moved on?
     *
     * <p>A null {@code source_updated_at} (a pre-V39 row) means "unknown", and
     * unknown resolves to CURRENT rather than inventing a CHANGED the author would
     * have no way to act on.
     */
    String sourceState(ReviewRequest request, ReviewSnapshot snapshot) throws SQLException {
        Optional<Chapter> live = chapterDao.findById(request.getSourceEntityId());
        if (live.isEmpty()) {
            return ReviewRequestSummary.SOURCE_DELETED;
        }

        Instant captured = snapshot == null ? null : snapshot.getSourceUpdatedAt();
        Instant current  = live.get().getUpdatedAt();
        if (captured == null || current == null) {
            return ReviewRequestSummary.SOURCE_CURRENT;
        }

        return current.isAfter(captured)
                ? ReviewRequestSummary.SOURCE_CHANGED
                : ReviewRequestSummary.SOURCE_CURRENT;
    }

    /** The author's own request, with its snapshot. Author-scoped; 404 otherwise. */
    public ReviewRequest requireOwned(UUID authorUserId, UUID requestId) throws SQLException {
        ReviewRequest r = requestDao.findById(requestId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "No such review request."));

        // 404, not 403 — the caller learns nothing about requests that are not theirs.
        if (!r.getAuthorUserId().equals(authorUserId)) {
            throw new ReviewException(404, "not_found", "No such review request.");
        }
        return r;
    }

    public ReviewSnapshot snapshotFor(UUID authorUserId, UUID requestId) throws SQLException {
        requireOwned(authorUserId, requestId);
        return snapshotDao.findByRequestId(requestId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "No snapshot for this request."));
    }

    // =========================================================================
    // Edit and lifecycle
    // =========================================================================

    /** Metadata only. The snapshot and the source are unreachable from here. */
    public ReviewRequest updateMetadata(UUID authorUserId, UUID requestId, ReviewRequest form)
            throws SQLException {

        ReviewRequest existing = requireOwned(authorUserId, requestId);

        // A withdrawn or administratively removed request is history, not a draft.
        if (ReviewRequestDao.STATUS_WITHDRAWN.equals(existing.getStatus())
                || ReviewRequestDao.STATUS_REMOVED.equals(existing.getStatus())) {
            throw new ReviewException(409, "not_editable",
                    "A withdrawn or removed request can no longer be edited.");
        }

        ReviewRequest merged = ReviewRequest.builder()
                .title(requireTitle(form, null))
                .description(trimToNull(form.getDescription()))
                .authorQuestions(trimToNull(form.getAuthorQuestions()))
                .genre(trimToNull(form.getGenre()))
                .feedbackTypes(form.getFeedbackTypes())
                .contentWarnings(trimToNull(form.getContentWarnings()))
                .visibility(visibilityOrDefault(form.getVisibility()))
                .maxReviews(positiveOrNull(form.getMaxReviews()))
                .closesAt(form.getClosesAt())
                .build();

        return requestDao.update(requestId, authorUserId, merged)
                .orElseThrow(() -> new ReviewException(404, "not_found", "No such review request."));
    }

    public ReviewRequest pause(UUID authorUserId, UUID requestId) throws SQLException {
        return transition(authorUserId, requestId, ReviewRequestDao.STATUS_PAUSED, null,
                ReviewRequestDao.STATUS_OPEN);
    }

    public ReviewRequest resume(UUID authorUserId, UUID requestId) throws SQLException {
        return transition(authorUserId, requestId, ReviewRequestDao.STATUS_OPEN, null,
                ReviewRequestDao.STATUS_PAUSED);
    }

    public ReviewRequest close(UUID authorUserId, UUID requestId) throws SQLException {
        return transition(authorUserId, requestId, ReviewRequestDao.STATUS_CLOSED, Instant.now(),
                ReviewRequestDao.STATUS_OPEN, ReviewRequestDao.STATUS_PAUSED);
    }

    /**
     * The author retracts the package. It leaves the queue and every reviewer view,
     * but submitted reviews are retained — spec §30.2 Q8 requires records to survive
     * for dispute handling, and an author must not be able to erase feedback they
     * did not like by withdrawing after the fact.
     */
    public ReviewRequest withdraw(UUID authorUserId, UUID requestId) throws SQLException {
        return transition(authorUserId, requestId, ReviewRequestDao.STATUS_WITHDRAWN, Instant.now(),
                ReviewRequestDao.STATUS_DRAFT, ReviewRequestDao.STATUS_OPEN,
                ReviewRequestDao.STATUS_PAUSED, ReviewRequestDao.STATUS_CLOSED);
    }

    private ReviewRequest transition(UUID authorUserId, UUID requestId, String target,
            Instant closedAt, String... legalFrom) throws SQLException {

        ReviewRequest existing = requireOwned(authorUserId, requestId);

        boolean legal = false;
        for (String from : legalFrom) {
            if (from.equals(existing.getStatus())) {
                legal = true;
                break;
            }
        }
        if (!legal) {
            throw new ReviewException(409, "invalid_transition",
                    "Cannot move a " + existing.getStatus() + " request to " + target + ".");
        }

        // Closing keeps the original closed_at if the request was already closed; a
        // withdraw after a close should not rewrite when it left the queue.
        Instant stamp = closedAt == null ? null
                : (existing.getClosedAt() != null ? existing.getClosedAt() : closedAt);

        return requestDao.setStatus(requestId, authorUserId, target, stamp)
                .orElseThrow(() -> new ReviewException(404, "not_found", "No such review request."));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String sourceTitle(Chapter chapter) {
        String title = trimToNull(chapter.getTitle());
        return title != null ? title : "Chapter " + chapter.getChapterNumber();
    }

    /**
     * The package title. Falls back to the chapter's own title at publish rather
     * than rejecting the request — an author who typed nothing meant "call it what
     * the chapter is called."
     */
    private static String requireTitle(ReviewRequest form, Chapter fallback) {
        String title = trimToNull(form.getTitle());
        if (title != null) {
            return title.length() > 200 ? title.substring(0, 200) : title;
        }
        if (fallback != null) {
            return sourceTitle(fallback);
        }
        throw new ReviewException(400, "title_required", "A package title is required.");
    }

    private static String visibilityOrDefault(String visibility) {
        return ReviewRequestDao.VISIBILITY_INVITE.equals(visibility)
                ? ReviewRequestDao.VISIBILITY_INVITE
                : ReviewRequestDao.VISIBILITY_PUBLIC;
    }

    private static Integer positiveOrNull(Integer maxReviews) {
        return maxReviews == null || maxReviews <= 0 ? null : maxReviews;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
