package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.review.ReviewProfile;
import com.richardsand.novelkms.model.review.ReviewRequest;
import com.richardsand.novelkms.model.review.ReviewRequestSummary;
import com.richardsand.novelkms.model.review.ReviewSnapshot;

/**
 * Publishing is the single point where the review network touches the manuscript,
 * so it is where the guarantees the rest of the network rests on are either
 * established or lost:
 *
 * <ul>
 *   <li><b>The snapshot is genuinely frozen.</b> Editing — or hard-deleting — the
 *       source chapter must leave published text untouched. The chapter table has
 *       no foreign key pointing at it, and that is load-bearing, not incidental.</li>
 *   <li><b>The word count is recomputed, not inherited.</b> {@code scene.word_count}
 *       was historically zeroed by the pre-V37 autosave path. A snapshot's count is
 *       the number reviewers judge a package by and every contribution metric is
 *       built from, so it must be right at capture time even when the column lies.</li>
 *   <li><b>Codex rows are not manuscript.</b> Codex categories are stored as chapter
 *       rows ({@code codex_id} set, {@code book_id} null). Publishing one would leak
 *       an author's private worldbuilding into a public queue.</li>
 * </ul>
 */
class ReviewPublishServiceTest extends NovelKmsTestBase {

    private static final ReviewProfileDao  PROFILES  = new ReviewProfileDao(ds);
    private static final ReviewRequestDao  REQUESTS  = new ReviewRequestDao(ds);
    private static final ReviewSnapshotDao SNAPSHOTS = new ReviewSnapshotDao(ds);

    private static final ReviewPublishService SERVICE = new ReviewPublishService(
            ds, chapterDao, sceneDao, bookDao, projectDao, PROFILES, REQUESTS, SNAPSHOTS);

    private Book    book;
    private Chapter chapter;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();

        PROFILES.create(TEST_USER_ID, ReviewProfile.builder().handle("Author_One").build());

        Project project = createTestProject("The Long Road", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
        chapter = chapterDao.create(book.getId(), null, "The Crossing", null, null);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Adds a scene with content, letting SceneDao compute the counts as the editor would. */
    private UUID scene(String title, String html) throws SQLException {
        UUID id = sceneDao.create(chapter.getId(), title, null).getId();
        sceneDao.saveContent(id, html, 0);
        return id;
    }

    /** An empty publish form — the author filled in nothing. */
    private static ReviewRequest form() {
        return ReviewRequest.builder().build();
    }

    private static ReviewRequest form(String title) {
        return ReviewRequest.builder().title(title).build();
    }

    private ReviewRequest publish() throws SQLException {
        return SERVICE.publishChapter(TEST_USER_ID, chapter.getId(), form("Crossing — draft 3"));
    }

    private static ReviewException expect(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReviewException.class, e);
    }

    // =========================================================================
    // Snapshot assembly
    // =========================================================================

    @Test
    void publish_joinsScenesInOrderWithABareSceneBreak() throws SQLException {
        scene("One", "<p>The river was high.</p>");
        scene("Two", "<p>She waited for dark.</p>");

        ReviewRequest  r = publish();
        ReviewSnapshot s = SNAPSHOTS.findByRequestId(r.getId()).orElseThrow();

        assertEquals("<p>The river was high.</p><hr><p>She waited for dark.</p>", s.getContentHtml());
    }

    /**
     * The editor emits {@code <hr data-scene-after="{uuid}">}. A snapshot must not:
     * SceneBreak.js already parses a bare {@code <hr>}, and the attribute would hand
     * a stranger the internal UUIDs of the author's scenes for nothing in return.
     */
    @Test
    void publish_sceneBreakCarriesNoSceneIds() throws SQLException {
        UUID first = scene("One", "<p>Alpha.</p>");
        scene("Two", "<p>Beta.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertFalse(s.getContentHtml().contains("data-scene-after"));
        assertFalse(s.getContentHtml().contains(first.toString()));
    }

    @Test
    void publish_singleScene_hasNoSceneBreak() throws SQLException {
        scene("Only", "<p>Alone on the bank.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals("<p>Alone on the bank.</p>", s.getContentHtml());
        assertFalse(s.getContentHtml().contains("<hr>"));
    }

    /**
     * A placeholder scene must not become a visible hole between two rules in the
     * published package.
     */
    @Test
    void publish_dropsBlankScenes() throws SQLException {
        scene("One", "<p>Real text.</p>");
        scene("Placeholder", "<p></p>");
        scene("Three", "<p>More real text.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals("<p>Real text.</p><hr><p>More real text.</p>", s.getContentHtml());
    }

    // =========================================================================
    // Word count
    // =========================================================================

    @Test
    void publish_countsWordsAcrossScenes() throws SQLException {
        scene("One", "<p>One two three.</p>");
        scene("Two", "<p>Four five.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals(5, s.getWordCount());
    }

    /**
     * The one that matters. {@code scene.word_count} is corrupted here to imitate the
     * pre-V37 autosave bug that zeroed it. The snapshot must still count correctly,
     * because it recomputes from the assembled HTML rather than summing the column.
     */
    @Test
    void publish_recomputesWordCount_evenWhenSceneColumnsAreStale() throws SQLException {
        UUID one = scene("One", "<p>One two three.</p>");
        UUID two = scene("Two", "<p>Four five.</p>");

        corruptWordCount(one, 0);
        corruptWordCount(two, 9999);

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals(5, s.getWordCount(),
                "the snapshot must count the text, not trust scene.word_count");
    }

    /** Tag boundaries must not merge words: "end</p><p>start" is two words, not one. */
    @Test
    void publish_doesNotMergeWordsAcrossTagBoundaries() throws SQLException {
        scene("One", "<p>end</p><p>start</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals(2, s.getWordCount());
    }

    private static void corruptWordCount(UUID sceneId, int bogus) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE scene SET word_count = ? WHERE id = ?")) {
            ps.setInt(1, bogus);
            ps.setObject(2, sceneId);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Preconditions
    // =========================================================================

    @Test
    void publish_withoutAProfile_requiresOne() throws SQLException {
        scene("One", "<p>Text.</p>");

        // OTHER_USER_ID has never claimed a handle.
        ReviewException e = expect(() -> SERVICE.publishChapter(OTHER_USER_ID, chapter.getId(), form()));

        assertEquals(409, e.status());
        assertEquals("profile_required", e.code());
    }

    @Test
    void publish_chapterWithNoScenes_isEmpty() {
        ReviewException e = expect(() -> SERVICE.publishChapter(TEST_USER_ID, chapter.getId(), form()));

        assertEquals(400, e.status());
        assertEquals("empty_chapter", e.code());
    }

    @Test
    void publish_chapterWithOnlyBlankScenes_isEmpty() throws SQLException {
        scene("One", "<p></p>");
        scene("Two", "   ");

        ReviewException e = expect(() -> SERVICE.publishChapter(TEST_USER_ID, chapter.getId(), form()));

        assertEquals(400, e.status());
        assertEquals("empty_chapter", e.code());
    }

    @Test
    void publish_unknownChapter_isNotFound() {
        ReviewException e = expect(
                () -> SERVICE.publishChapter(TEST_USER_ID, UUID.randomUUID(), form()));

        assertEquals(404, e.status());
    }

    /**
     * Codex categories are chapter rows with codex_id set and book_id null.
     * Publishing one would put an author's private worldbuilding in a public queue.
     */
    @Test
    void publish_codexChapter_isRejected() throws SQLException {
        Chapter codexCategory = chapterDao.createCodexChapter(UUID.randomUUID(), "CANON", "Canon");

        ReviewException e = expect(
                () -> SERVICE.publishChapter(TEST_USER_ID, codexCategory.getId(), form()));

        assertEquals(400, e.status());
        assertEquals("not_manuscript", e.code());
    }

    // =========================================================================
    // What publication records
    // =========================================================================

    @Test
    void publish_opensThePackagePubliclyByDefault() throws SQLException {
        scene("One", "<p>Text here.</p>");

        ReviewRequest r = publish();

        assertEquals(ReviewRequestDao.STATUS_OPEN, r.getStatus());
        assertEquals(ReviewRequestDao.VISIBILITY_PUBLIC, r.getVisibility());
        assertEquals(ReviewRequestDao.SCOPE_CHAPTER, r.getSourceScope());
        assertEquals(chapter.getId(), r.getSourceEntityId());
        assertEquals(TEST_USER_ID, r.getAuthorUserId());
        assertNotNull(r.getPublishedAt());
        assertNull(r.getClosedAt());
    }

    @Test
    void publish_freezesTheTitlesSoTheyOutliveTheManuscript() throws SQLException {
        scene("One", "<p>Text here.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertEquals("The Crossing", s.getSourceTitle());
        assertEquals("Book One", s.getBookTitle());
        assertEquals("The Long Road", s.getProjectTitle());
    }

    @Test
    void publish_untitledChapter_fallsBackToChapterNumber() throws SQLException {
        Chapter untitled = chapterDao.create(book.getId(), null, null, null, null);
        UUID    sceneId  = sceneDao.create(untitled.getId(), "One", null).getId();
        sceneDao.saveContent(sceneId, "<p>Words.</p>", 0);

        ReviewRequest  r = SERVICE.publishChapter(TEST_USER_ID, untitled.getId(), form());
        ReviewSnapshot s = SNAPSHOTS.findByRequestId(r.getId()).orElseThrow();

        assertTrue(s.getSourceTitle().startsWith("Chapter "), s.getSourceTitle());
        // No package title supplied either — it falls back to the same thing.
        assertEquals(s.getSourceTitle(), r.getTitle());
    }

    @Test
    void publish_capturesTheSourceVersionMarker() throws SQLException {
        scene("One", "<p>Text here.</p>");

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(publish().getId()).orElseThrow();

        assertNotNull(s.getSourceUpdatedAt(), "V39's version marker must be recorded at capture");
    }

    /** Both rows or neither: a request with no snapshot is a package with no manuscript. */
    @Test
    void publish_writesRequestAndSnapshotTogether() throws SQLException {
        scene("One", "<p>Text here.</p>");

        ReviewRequest r = publish();

        assertTrue(REQUESTS.findById(r.getId()).isPresent());
        assertTrue(SNAPSHOTS.findByRequestId(r.getId()).isPresent());
    }

    // =========================================================================
    // Immutability — the whole point of the snapshot
    // =========================================================================

    @Test
    void editingTheChapterAfterPublishing_doesNotChangeTheSnapshot() throws SQLException {
        UUID sceneId = scene("One", "<p>Original text.</p>");

        ReviewRequest r = publish();

        sceneDao.saveContent(sceneId, "<p>Completely rewritten.</p>", 0);

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(r.getId()).orElseThrow();
        assertEquals("<p>Original text.</p>", s.getContentHtml());
        assertEquals(2, s.getWordCount());
    }

    /**
     * There is no foreign key from review_snapshot to chapter, on purpose. A hard
     * delete of the source must not cascade away review history — nor block.
     */
    @Test
    void deletingTheChapter_leavesTheSnapshotIntact() throws SQLException {
        scene("One", "<p>Original text.</p>");

        ReviewRequest r = publish();

        assertTrue(chapterDao.delete(chapter.getId()));

        ReviewSnapshot s = SNAPSHOTS.findByRequestId(r.getId()).orElseThrow();
        assertEquals("<p>Original text.</p>", s.getContentHtml());
        assertEquals("The Crossing", s.getSourceTitle());
        assertEquals("Book One", s.getBookTitle());
        assertTrue(REQUESTS.findById(r.getId()).isPresent());
    }

    @Test
    void republishing_createsANewRequestAndANewSnapshot() throws SQLException {
        scene("One", "<p>First pass.</p>");

        ReviewRequest first = publish();
        ReviewRequest second = SERVICE.publishChapter(TEST_USER_ID, chapter.getId(), form("Draft 4"));

        assertFalse(first.getId().equals(second.getId()));

        ReviewSnapshot a = SNAPSHOTS.findByRequestId(first.getId()).orElseThrow();
        ReviewSnapshot b = SNAPSHOTS.findByRequestId(second.getId()).orElseThrow();
        assertFalse(a.getId().equals(b.getId()));
    }

    // =========================================================================
    // Source state
    // =========================================================================

    @Test
    void sourceState_isCurrentRightAfterPublishing() throws SQLException {
        scene("One", "<p>Text here.</p>");
        publish();

        List<ReviewRequestSummary> mine = SERVICE.mine(TEST_USER_ID);

        assertEquals(1, mine.size());
        assertEquals(ReviewRequestSummary.SOURCE_CURRENT, mine.get(0).getSourceState());
    }

    @Test
    void sourceState_becomesChangedWhenTheChapterIsEdited() throws SQLException {
        scene("One", "<p>Text here.</p>");
        publish();

        // The version marker has microsecond resolution; a deliberate pause keeps this
        // from turning into a timing-flaky test on a fast machine.
        sleep();
        chapterDao.update(chapter.getId(), "The Crossing (revised)", null, null, false);

        assertEquals(ReviewRequestSummary.SOURCE_CHANGED,
                SERVICE.mine(TEST_USER_ID).get(0).getSourceState());
    }

    @Test
    void sourceState_becomesDeletedWhenTheChapterIsRemoved() throws SQLException {
        scene("One", "<p>Text here.</p>");
        publish();

        chapterDao.delete(chapter.getId());

        ReviewRequestSummary summary = SERVICE.mine(TEST_USER_ID).get(0);
        assertEquals(ReviewRequestSummary.SOURCE_DELETED, summary.getSourceState());

        // The package itself is unharmed — it still knows what it was.
        assertEquals("The Crossing", summary.getSourceTitle());
        assertEquals(3, summary.getWordCount());
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // My Requests
    // =========================================================================

    @Test
    void mine_returnsNewestFirst_withSnapshotMetadataAndNoContent() throws SQLException {
        scene("One", "<p>One two three.</p>");

        publish();
        sleep();
        SERVICE.publishChapter(TEST_USER_ID, chapter.getId(), form("Second package"));

        List<ReviewRequestSummary> mine = SERVICE.mine(TEST_USER_ID);

        assertEquals(2, mine.size());
        assertEquals("Second package", mine.get(0).getTitle());
        assertEquals("The Crossing", mine.get(0).getSourceTitle());
        assertEquals(3, mine.get(0).getWordCount());
        assertNotNull(mine.get(0).getSnapshotCreatedAt());
        assertEquals(0, mine.get(0).getReviewCount());
    }

    @Test
    void mine_isScopedToTheAuthor() throws SQLException {
        scene("One", "<p>Text.</p>");
        publish();

        assertEquals(List.of(), SERVICE.mine(OTHER_USER_ID));
    }

    @Test
    void requireOwned_anotherAuthorsRequest_is404NotForbidden() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewException e = expect(() -> SERVICE.requireOwned(OTHER_USER_ID, r.getId()));

        assertEquals(404, e.status(), "403 would confirm the request exists");
    }

    // =========================================================================
    // Edit
    // =========================================================================

    @Test
    void updateMetadata_changesTheEditableFieldsOnly() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewRequest updated = SERVICE.updateMetadata(TEST_USER_ID, r.getId(),
                ReviewRequest.builder()
                        .title("Retitled")
                        .description("Now with a description.")
                        .genre("literary")
                        .feedbackTypes(List.of("developmental", "line"))
                        .visibility(ReviewRequestDao.VISIBILITY_INVITE)
                        .build());

        assertEquals("Retitled", updated.getTitle());
        assertEquals("Now with a description.", updated.getDescription());
        assertEquals(List.of("developmental", "line"), updated.getFeedbackTypes());
        assertEquals(ReviewRequestDao.VISIBILITY_INVITE, updated.getVisibility());

        // Untouchable from here, no matter what the payload says.
        assertEquals(ReviewRequestDao.STATUS_OPEN, updated.getStatus());
        assertEquals(chapter.getId(), updated.getSourceEntityId());
        assertEquals(ReviewRequestDao.SCOPE_CHAPTER, updated.getSourceScope());
    }

    @Test
    void updateMetadata_cannotTouchAnotherAuthorsRequest() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewException e = expect(
                () -> SERVICE.updateMetadata(OTHER_USER_ID, r.getId(), form("Hijacked")));

        assertEquals(404, e.status());
        assertEquals("Crossing — draft 3", REQUESTS.findById(r.getId()).orElseThrow().getTitle());
    }

    @Test
    void updateMetadata_onAWithdrawnRequest_isRejected() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();
        SERVICE.withdraw(TEST_USER_ID, r.getId());

        ReviewException e = expect(
                () -> SERVICE.updateMetadata(TEST_USER_ID, r.getId(), form("Too late")));

        assertEquals(409, e.status());
        assertEquals("not_editable", e.code());
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    void pauseAndResume_moveBetweenOpenAndPaused() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        assertEquals(ReviewRequestDao.STATUS_PAUSED,
                SERVICE.pause(TEST_USER_ID, r.getId()).getStatus());
        assertEquals(ReviewRequestDao.STATUS_OPEN,
                SERVICE.resume(TEST_USER_ID, r.getId()).getStatus());
    }

    @Test
    void close_stampsWhenItLeftTheQueue() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewRequest closed = SERVICE.close(TEST_USER_ID, r.getId());

        assertEquals(ReviewRequestDao.STATUS_CLOSED, closed.getStatus());
        assertNotNull(closed.getClosedAt());
    }

    @Test
    void close_isLegalFromPausedToo() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();
        SERVICE.pause(TEST_USER_ID, r.getId());

        assertEquals(ReviewRequestDao.STATUS_CLOSED,
                SERVICE.close(TEST_USER_ID, r.getId()).getStatus());
    }

    @Test
    void withdraw_isLegalFromAnyLiveState() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewRequest withdrawn = SERVICE.withdraw(TEST_USER_ID, r.getId());

        assertEquals(ReviewRequestDao.STATUS_WITHDRAWN, withdrawn.getStatus());
        assertNotNull(withdrawn.getClosedAt());
    }

    /**
     * A withdraw after a close must not rewrite when the package left the queue —
     * that timestamp is part of the record, and the record has to survive for
     * dispute handling.
     */
    @Test
    void withdraw_afterClose_keepsTheOriginalClosedAt() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewRequest closed = SERVICE.close(TEST_USER_ID, r.getId());
        sleep();
        ReviewRequest withdrawn = SERVICE.withdraw(TEST_USER_ID, r.getId());

        assertEquals(closed.getClosedAt(), withdrawn.getClosedAt());
    }

    @Test
    void resume_fromOpen_isAnInvalidTransition() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewException e = expect(() -> SERVICE.resume(TEST_USER_ID, r.getId()));

        assertEquals(409, e.status());
        assertEquals("invalid_transition", e.code());
    }

    @Test
    void pause_afterClose_isAnInvalidTransition() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();
        SERVICE.close(TEST_USER_ID, r.getId());

        ReviewException e = expect(() -> SERVICE.pause(TEST_USER_ID, r.getId()));

        assertEquals(409, e.status());
        assertEquals("invalid_transition", e.code());
    }

    @Test
    void withdraw_twice_isAnInvalidTransition() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();
        SERVICE.withdraw(TEST_USER_ID, r.getId());

        ReviewException e = expect(() -> SERVICE.withdraw(TEST_USER_ID, r.getId()));

        assertEquals(409, e.status());
    }

    @Test
    void lifecycle_cannotBeMovedByAnotherAuthor() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        assertEquals(404, expect(() -> SERVICE.close(OTHER_USER_ID, r.getId())).status());
        assertEquals(ReviewRequestDao.STATUS_OPEN, REQUESTS.findById(r.getId()).orElseThrow().getStatus());
    }

    // =========================================================================
    // Snapshot read
    // =========================================================================

    @Test
    void snapshotFor_returnsTheFrozenTextToItsAuthor() throws SQLException {
        scene("One", "<p>Frozen.</p>");
        ReviewRequest r = publish();

        ReviewSnapshot s = SERVICE.snapshotFor(TEST_USER_ID, r.getId());

        assertEquals("<p>Frozen.</p>", s.getContentHtml());
    }

    @Test
    void snapshotFor_anotherAuthorsRequest_is404() throws SQLException {
        scene("One", "<p>Frozen.</p>");
        ReviewRequest r = publish();

        assertEquals(404, expect(() -> SERVICE.snapshotFor(OTHER_USER_ID, r.getId())).status());
    }

    /** The list read must not drag whole chapters across the wire to render titles. */
    @Test
    void listReads_doNotLoadSnapshotContent() throws SQLException {
        scene("One", "<p>Text.</p>");
        ReviewRequest r = publish();

        ReviewSnapshot meta = SNAPSHOTS.findMetaByRequestIds(List.of(r.getId())).get(r.getId());

        assertNotNull(meta);
        assertNull(meta.getContentHtml());
        assertEquals(1, meta.getWordCount());
    }
}
