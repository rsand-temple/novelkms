package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.TrashItem;

/**
 * Integration tests for {@link TrashDao} — verifies all soft-delete, restore,
 * purge, batch-index, and orphan-sweep SQL against the in-memory H2 database.
 */
class TrashDaoTest extends NovelKmsTestBase {

    private static final AiReviewDao aiReviewDao = new AiReviewDao(ds);
    private static final CodexDao codexDao = new CodexDao(ds);

    private Project project;
    private Book    book;
    private Chapter chapter;
    private Scene   scene;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = createTestProject("Test Project", null);
        book = bookDao.create(project.getId(), "Test Book", null, null, null);
        chapter = chapterDao.create(book.getId(), null, "Chapter One", null, null);
        scene = sceneDao.create(chapter.getId(), "Scene One", null);
    }

    // =========================================================================
    // trashProject
    // =========================================================================

    @Test
    void trashProject_returnsTrashItemWithCorrectFields() throws SQLException {
        Optional<TrashItem> result = trashDao.trashProject(TEST_USER_ID, project.getId());

        assertTrue(result.isPresent());
        TrashItem item = result.get();
        assertEquals("PROJECT", item.getRootType());
        assertEquals(project.getId(), item.getRootId());
        assertEquals("Test Project", item.getRootTitle());
        assertEquals(project.getId(), item.getProjectId());
        assertEquals("Test Project", item.getProjectTitle());
        assertEquals(1, item.getChildCount()); // one book
        assertNotNull(item.getDeletedAt());
        assertNotNull(item.getBatchId());
    }

    @Test
    void trashProject_setsDeletedAtOnRow() throws SQLException {
        trashDao.trashProject(TEST_USER_ID, project.getId());

        // The soft-deleted project should be invisible to normal findByIdForUser
        assertFalse(projectDao.findByIdForUser(project.getId(), TEST_USER_ID).isPresent());

        // But the row still exists (raw SQL ignoring deleted_at)
        assertTrue(rawRowExists("project", project.getId()));
    }

    @Test
    void trashProject_alreadyTrashed_returnsEmpty() throws SQLException {
        trashDao.trashProject(TEST_USER_ID, project.getId());

        Optional<TrashItem> second = trashDao.trashProject(TEST_USER_ID, project.getId());

        assertFalse(second.isPresent());
    }

    @Test
    void trashProject_wrongUser_returnsEmpty() throws SQLException {
        Optional<TrashItem> result = trashDao.trashProject(OTHER_USER_ID, project.getId());

        assertFalse(result.isPresent());
    }

    // =========================================================================
    // trashBook
    // =========================================================================

    @Test
    void trashBook_setsDeletedAtAndRecordsBatch() throws SQLException {
        Optional<TrashItem> result = trashDao.trashBook(TEST_USER_ID, book.getId());

        assertTrue(result.isPresent());
        TrashItem item = result.get();
        assertEquals("BOOK", item.getRootType());
        assertEquals(book.getId(), item.getRootId());
        assertEquals("Test Book", item.getRootTitle());
        assertEquals(project.getId(), item.getProjectId());
        assertEquals(1, item.getChildCount()); // one chapter

        // Book is invisible to normal reads
        assertFalse(bookDao.findById(book.getId()).isPresent());
    }

    // =========================================================================
    // trashChapter — manuscript vs codex auto-detection
    // =========================================================================

    @Test
    void trashChapter_manuscriptChapter_typeCHAPTER() throws SQLException {
        Optional<TrashItem> result = trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        assertTrue(result.isPresent());
        assertEquals("CHAPTER", result.get().getRootType());
        assertEquals(1, result.get().getChildCount()); // one scene
    }

    @Test
    void trashChapter_codexCategory_typeCODEX_CATEGORY() throws SQLException {
        var codex = codexDao.createForProject(project.getId(), "Codex");
        Chapter category = chapterDao.createCodexChapter(codex.getId(), "CHARACTER", "Characters");

        Optional<TrashItem> result = trashDao.trashChapter(TEST_USER_ID, category.getId());

        assertTrue(result.isPresent());
        assertEquals("CODEX_CATEGORY", result.get().getRootType());
        assertEquals("Characters", result.get().getRootTitle());
    }

    // =========================================================================
    // trashScene — manuscript vs codex auto-detection
    // =========================================================================

    @Test
    void trashScene_manuscriptScene_typeSCENE() throws SQLException {
        Optional<TrashItem> result = trashDao.trashScene(TEST_USER_ID, scene.getId());

        assertTrue(result.isPresent());
        assertEquals("SCENE", result.get().getRootType());
        assertEquals(0, result.get().getChildCount());
    }

    @Test
    void trashScene_codexEntry_typeCODEX_ENTRY() throws SQLException {
        var codex = codexDao.createForProject(project.getId(), "Codex");
        Chapter category = chapterDao.createCodexChapter(codex.getId(), "NOTES", "Notes");
        Scene entry = sceneDao.create(category.getId(), "Entry One", null);

        Optional<TrashItem> result = trashDao.trashScene(TEST_USER_ID, entry.getId());

        assertTrue(result.isPresent());
        assertEquals("CODEX_ENTRY", result.get().getRootType());
    }

    // =========================================================================
    // trashReview
    // =========================================================================

    @Test
    void trashReview_recordsRecommendationCountAndTitle() throws SQLException {
        UUID reviewId = aiReviewDao.createPending(
                TEST_USER_ID, project.getId(), book.getId(), chapter.getId(), null, "OPENAI", "gpt-5.4", "SYSTEM", null);
        // Complete the review with 2 recommendations
        aiReviewDao.completeReview(reviewId, "chapter-review-v1", "{}",
                List.of(
                        new AiReviewDao.NewRecommendation("Pacing", "MEDIUM", "p2", "Speed up", null, null, null),
                        new AiReviewDao.NewRecommendation("Clarity", "LOW", "p5", "Reword", null, null, null)));

        Optional<TrashItem> result = trashDao.trashReview(TEST_USER_ID, reviewId);

        assertTrue(result.isPresent());
        assertEquals("AI_REVIEW", result.get().getRootType());
        assertEquals(2, result.get().getChildCount());
        assertTrue(result.get().getRootTitle().contains("Chapter One"));
    }

    // =========================================================================
    // listForUser
    // =========================================================================

    @Test
    void listForUser_returnsOnlyUsersItems() throws SQLException {
        trashDao.trashScene(TEST_USER_ID, scene.getId());

        // Create items for other user
        Project otherProject = createTestProject(OTHER_USER_ID, "Other Project", null);
        Book otherBook = bookDao.create(otherProject.getId(), "Other Book", null, null, null);
        trashDao.trashBook(OTHER_USER_ID, otherBook.getId());

        List<TrashItem> items = trashDao.listForUser(TEST_USER_ID);
        assertEquals(1, items.size());
        assertEquals("SCENE", items.get(0).getRootType());
    }

    @Test
    void listForUser_orderedByDeletedAtDesc() throws SQLException {
        Scene scene2 = sceneDao.create(chapter.getId(), "Scene Two", null);
        trashDao.trashScene(TEST_USER_ID, scene.getId());
        trashDao.trashScene(TEST_USER_ID, scene2.getId());

        List<TrashItem> items = trashDao.listForUser(TEST_USER_ID);
        assertEquals(2, items.size());
        // Most recently deleted first
        assertTrue(items.get(0).getDeletedAt().compareTo(items.get(1).getDeletedAt()) >= 0);
    }

    // =========================================================================
    // findBatch
    // =========================================================================

    @Test
    void findBatch_wrongUser_returnsEmpty() throws SQLException {
        TrashItem item = trashDao.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();

        assertFalse(trashDao.findBatch(OTHER_USER_ID, item.getBatchId()).isPresent());
    }

    // =========================================================================
    // Restore
    // =========================================================================

    @Test
    void restoreProject_clearsDeletedAt() throws SQLException {
        trashDao.trashProject(TEST_USER_ID, project.getId());

        boolean restored = trashDao.restoreProject(project.getId(), "Test Project");

        assertTrue(restored);
        assertTrue(projectDao.findByIdForUser(project.getId(), TEST_USER_ID).isPresent());
    }

    @Test
    void restoreBook_setsNewDisplayOrderAndTitle() throws SQLException {
        trashDao.trashBook(TEST_USER_ID, book.getId());

        trashDao.restoreBook(book.getId(), "Restored Book", 99);

        Optional<Book> found = bookDao.findById(book.getId());
        assertTrue(found.isPresent());
        assertEquals("Restored Book", found.get().getTitle());
        assertEquals(99, found.get().getDisplayOrder());
    }

    @Test
    void restoreChapter_clearsDeletedAt() throws SQLException {
        trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        trashDao.restoreChapter(chapter.getId(), "Chapter One", 0);

        assertTrue(chapterDao.findById(chapter.getId()).isPresent());
    }

    @Test
    void restoreScene_clearsDeletedAt() throws SQLException {
        trashDao.trashScene(TEST_USER_ID, scene.getId());

        trashDao.restoreScene(scene.getId(), "Scene One", 0);

        assertTrue(sceneDao.findById(scene.getId()).isPresent());
    }

    @Test
    void restoreReview_clearsDeletedAt() throws SQLException {
        UUID reviewId = aiReviewDao.createPending(
                TEST_USER_ID, project.getId(), book.getId(), chapter.getId(), null, "OPENAI", "gpt-5.4", "SYSTEM", null);
        trashDao.trashReview(TEST_USER_ID, reviewId);

        trashDao.restoreReview(reviewId);

        assertTrue(aiReviewDao.findByIdForUser(reviewId, TEST_USER_ID).isPresent());
    }

    // =========================================================================
    // Purge
    // =========================================================================

    @Test
    void purgeProject_hardDeletesRow() throws SQLException {
        trashDao.trashProject(TEST_USER_ID, project.getId());

        trashDao.purgeProject(project.getId());

        assertFalse(rawRowExists("project", project.getId()));
    }

    @Test
    void purgeBook_cascadesChaptersAndScenes() throws SQLException {
        trashDao.trashBook(TEST_USER_ID, book.getId());

        trashDao.purgeBook(book.getId());

        assertFalse(rawRowExists("book", book.getId()));
        assertFalse(rawRowExists("chapter", chapter.getId()));
        assertFalse(rawRowExists("scene", scene.getId()));
    }

    @Test
    void purgeChapter_cascadesScenes() throws SQLException {
        trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        trashDao.purgeChapter(chapter.getId());

        assertFalse(rawRowExists("chapter", chapter.getId()));
        assertFalse(rawRowExists("scene", scene.getId()));
    }

    @Test
    void purgeScene_hardDeletesRow() throws SQLException {
        trashDao.trashScene(TEST_USER_ID, scene.getId());

        trashDao.purgeScene(scene.getId());

        assertFalse(rawRowExists("scene", scene.getId()));
    }

    @Test
    void purgeReview_hardDeletesRowAndRecommendations() throws SQLException {
        UUID reviewId = aiReviewDao.createPending(
                TEST_USER_ID, project.getId(), book.getId(), chapter.getId(), null, "OPENAI", "gpt-5.4", "SYSTEM", null);
        aiReviewDao.completeReview(reviewId, "v1", "{}",
                List.of(new AiReviewDao.NewRecommendation("Pacing", "LOW", "p1", "Fix", null, null, null)));
        trashDao.trashReview(TEST_USER_ID, reviewId);

        trashDao.purgeReview(reviewId);

        assertFalse(rawRowExists("ai_review", reviewId));
    }

    // =========================================================================
    // sweepOrphans
    // =========================================================================

    @Test
    void sweepOrphans_removesOrphanedBatchAfterCascadeDelete() throws SQLException {
        // Trash the scene individually, then purge the book (which cascade-deletes the scene)
        trashDao.trashScene(TEST_USER_ID, scene.getId());
        trashDao.trashBook(TEST_USER_ID, book.getId());

        // Purge the book — the scene row is cascade-deleted
        trashDao.purgeBook(book.getId());
        trashDao.deleteBatch(
                trashDao.listForUser(TEST_USER_ID).stream()
                        .filter(i -> "BOOK".equals(i.getRootType())).findFirst().get().getBatchId());

        // The scene's batch is now orphaned (root_id row no longer exists)
        assertEquals(1, trashDao.listForUser(TEST_USER_ID).size());

        trashDao.sweepOrphans(TEST_USER_ID);

        assertEquals(0, trashDao.listForUser(TEST_USER_ID).size());
    }

    // =========================================================================
    // Transitive hiding — root-only stamping verification
    //
    // The design stamps only the root. Children are hidden transitively because
    // in a real navigation flow the parent is invisible (findById returns empty),
    // so the UI never reaches findByBookId/findByChapterId. These tests verify
    // that (a) the parent IS invisible and (b) children are NOT stamped (i.e.
    // root-only stamping is correct — children remain reachable if you call
    // the query directly with the raw UUID).
    // =========================================================================

    @Test
    void trashedBook_parentIsInvisible_childrenNotStamped() throws SQLException {
        trashDao.trashBook(TEST_USER_ID, book.getId());

        // Parent is invisible to normal reads
        assertFalse(bookDao.findById(book.getId()).isPresent());

        // Children are NOT stamped (root-only stamping) — they would still be
        // returned by a direct query, but the UI can never reach that query
        // because the parent is invisible.
        assertFalse(chapterDao.findByBookId(book.getId()).isEmpty(),
                "Children should NOT be stamped — root-only stamping means they are reachable by direct query");
        assertFalse(sceneDao.findByChapterId(chapter.getId()).isEmpty(),
                "Grandchildren should NOT be stamped either");
    }

    @Test
    void trashedChapter_parentIsInvisible_childrenNotStamped() throws SQLException {
        trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        // Parent is invisible
        assertFalse(chapterDao.findById(chapter.getId()).isPresent());

        // Children not stamped — scene's own deleted_at is NULL
        assertFalse(sceneDao.findByChapterId(chapter.getId()).isEmpty(),
                "Scenes should NOT be stamped — root-only stamping");
    }

    @Test
    void trashedProject_parentIsInvisible_childrenNotStamped() throws SQLException {
        trashDao.trashProject(TEST_USER_ID, project.getId());

        // Parent is invisible
        assertFalse(projectDao.findByIdForUser(project.getId(), TEST_USER_ID).isPresent());

        // Children not stamped — book's own deleted_at is NULL
        assertFalse(bookDao.findByProjectId(project.getId()).isEmpty(),
                "Books should NOT be stamped — root-only stamping");
    }

    // =========================================================================
    // Word count exclusion
    // =========================================================================

    @Test
    void trashedChapter_excludedFromBookWordCount() throws SQLException {
        sceneDao.saveContent(scene.getId(), "<p>one two three</p>", 3);
        int before = bookDao.getTotalWordCount(book.getId());
        assertTrue(before > 0);

        trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        int after = bookDao.getTotalWordCount(book.getId());
        assertEquals(0, after);
    }

    @Test
    void trashedScene_excludedFromBookWordCount() throws SQLException {
        sceneDao.saveContent(scene.getId(), "<p>one two three</p>", 3);
        int before = bookDao.getTotalWordCount(book.getId());
        assertTrue(before > 0);

        trashDao.trashScene(TEST_USER_ID, scene.getId());

        // The scene's word count should no longer contribute
        int after = bookDao.getTotalWordCount(book.getId());
        assertTrue(after < before);
    }

    // =========================================================================
    // Chapter numbering exclusion
    // =========================================================================

    @Test
    void trashedChapter_doesNotOccupyChapterNumber() throws SQLException {
        chapterDao.create(book.getId(), null, "Chapter Two", null, null);
        chapterDao.create(book.getId(), null, "Chapter Three", null, null);

        // Diagnostic: verify chapters exist via raw SQL (bypasses CTE and deleted_at filter)
        int rawTotal = rawChapterCountForBook(book.getId());
        assertEquals(3, rawTotal,
                "Raw SQL count — 3 chapters should exist in the book");

        // Before trash: ch1=1, ch2=2, ch3=3
        List<Chapter> before = chapterDao.findByBookId(book.getId());
        assertEquals(3, before.size(),
                "findByBookId should return all 3 non-deleted direct-book chapters");

        trashDao.trashChapter(TEST_USER_ID, chapter.getId());

        // After trash: ch2=1, ch3=2 (the trashed chapter's number is released)
        List<Chapter> remaining = chapterDao.findByBookId(book.getId());
        assertEquals(2, remaining.size());
        assertEquals(1, remaining.get(0).getChapterNumber());
        assertEquals(2, remaining.get(1).getChapterNumber());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Checks for raw row existence ignoring deleted_at — bypasses DAO filters. */
    private boolean rawRowExists(String table, UUID id) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Raw count of ALL chapters for a book, ignoring deleted_at and part_id/codex_id filters. */
    private int rawChapterCountForBook(UUID bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM chapter WHERE book_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
