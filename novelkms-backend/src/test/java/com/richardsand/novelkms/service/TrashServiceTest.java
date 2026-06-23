package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.AiReviewDao;
import com.richardsand.novelkms.dao.CodexDao;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.TrashItem;

/**
 * Tests for {@link TrashService} business logic: title collision de-duplication,
 * parent-liveness blocking (409), display-order appending, purge cascade with
 * orphan sweep, and empty-all.
 */
class TrashServiceTest extends NovelKmsTestBase {

    private static final AiReviewDao  aiReviewDao  = new AiReviewDao(ds);
    private static final CodexDao     codexDao     = new CodexDao(ds);

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
    // Restore — title collision de-duplication
    // =========================================================================

    @Test
    void restore_project_titleCollision_addsNumericSuffix() throws SQLException {
        // Create a second project with the same name, then trash the first
        createTestProject("Test Project", null);
        TrashItem trashed = trashService.trashProject(TEST_USER_ID, project.getId()).orElseThrow();

        TrashItem restored = trashService.restore(TEST_USER_ID, trashed.getBatchId());

        assertNotNull(restored);
        // The restored project should exist with a de-duped title
        List<Project> projects = projectDao.findAllForUser(TEST_USER_ID);
        assertTrue(projects.stream().anyMatch(p -> "Test Project (1)".equals(p.getTitle())));
    }

    @Test
    void restore_project_blankTitle_staysBlank() throws SQLException {
        // Create a project with blank title (unusual but valid for update path)
        Project blankProject = createTestProject("  ", null);
        TrashItem trashed = trashService.trashProject(TEST_USER_ID, blankProject.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        // Blank titles are left blank — no "(1)" suffix appended
        Project restored = projectDao.findByIdForUser(blankProject.getId(), TEST_USER_ID).orElseThrow();
        assertTrue(restored.getTitle().isBlank());
    }

    @Test
    void restore_book_titleCollision_addsNumericSuffix() throws SQLException {
        bookDao.create(project.getId(), "Test Book", null, null, null);
        TrashItem trashed = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        Book restored = bookDao.findById(book.getId()).orElseThrow();
        assertEquals("Test Book (1)", restored.getTitle());
    }

    @Test
    void restore_chapter_titleCollision_addsNumericSuffix() throws SQLException {
        chapterDao.create(book.getId(), null, "Chapter One", null, null);
        TrashItem trashed = trashService.trashChapter(TEST_USER_ID, chapter.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        Chapter restored = chapterDao.findById(chapter.getId()).orElseThrow();
        assertEquals("Chapter One (1)", restored.getTitle());
    }

    @Test
    void restore_scene_titleCollision_addsNumericSuffix() throws SQLException {
        sceneDao.create(chapter.getId(), "Scene One", null);
        TrashItem trashed = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        Scene restored = sceneDao.findById(scene.getId()).orElseThrow();
        assertEquals("Scene One (1)", restored.getTitle());
    }

    // =========================================================================
    // Restore — display order appending
    // =========================================================================

    @Test
    void restore_book_appendsToEndOfOrder() throws SQLException {
        Book book2 = bookDao.create(project.getId(), "Second Book", null, null, null);
        TrashItem trashed = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        Book restored = bookDao.findById(book.getId()).orElseThrow();
        // book2 is at order 1 (after book was trashed, it kept order 1);
        // restored book should be appended after all existing books
        assertTrue(restored.getDisplayOrder() > book2.getDisplayOrder());
    }

    @Test
    void restore_chapter_appendsToEndOfOrder() throws SQLException {
        Chapter ch2 = chapterDao.create(book.getId(), null, "Chapter Two", null, null);
        TrashItem trashed = trashService.trashChapter(TEST_USER_ID, chapter.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        Chapter restored = chapterDao.findById(chapter.getId()).orElseThrow();
        assertTrue(restored.getDisplayOrder() > ch2.getDisplayOrder());
    }

    // =========================================================================
    // Restore — parent-liveness blocking (409)
    // =========================================================================

    @Test
    void restore_book_parentProjectTrashed_throws409() throws SQLException {
        TrashItem trashedBook = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();
        trashService.trashProject(TEST_USER_ID, project.getId());

        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedBook.getBatchId()));
        assertEquals(409, ex.status());
        assertEquals("parent_unavailable", ex.code());
    }

    @Test
    void restore_chapter_parentBookTrashed_throws409() throws SQLException {
        TrashItem trashedChapter = trashService.trashChapter(TEST_USER_ID, chapter.getId()).orElseThrow();
        trashService.trashBook(TEST_USER_ID, book.getId());

        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedChapter.getBatchId()));
        assertEquals(409, ex.status());
    }

    @Test
    void restore_scene_parentChapterTrashed_throws409() throws SQLException {
        TrashItem trashedScene = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();
        trashService.trashChapter(TEST_USER_ID, chapter.getId());

        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedScene.getBatchId()));
        assertEquals(409, ex.status());
    }

    @Test
    void restore_review_chapterTrashed_throws409() throws SQLException {
        UUID reviewId = aiReviewDao.createPending(
                TEST_USER_ID, project.getId(), book.getId(), chapter.getId(), "OPENAI", "gpt-5.4");
        TrashItem trashedReview = trashService.trashReview(TEST_USER_ID, reviewId).orElseThrow();
        trashService.trashChapter(TEST_USER_ID, chapter.getId());

        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedReview.getBatchId()));
        assertEquals(409, ex.status());
    }

    @Test
    void restore_book_parentProjectPurged_batchSweptReturns404() throws SQLException {
        TrashItem trashedBook = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();
        TrashItem trashedProject = trashService.trashProject(TEST_USER_ID, project.getId()).orElseThrow();
        trashService.purge(TEST_USER_ID, trashedProject.getBatchId());

        // Purging the project cascade-deleted the book row; the orphan sweep
        // removed the book's batch. Attempting to restore yields 404 (not 409)
        // because the trash entry itself no longer exists.
        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedBook.getBatchId()));
        assertEquals(404, ex.status());
    }

    // =========================================================================
    // Restore — codex types
    // =========================================================================

    @Test
    void restore_codexCategory_parentProjectLive_succeeds() throws SQLException {
        var codex = codexDao.createForProject(project.getId(), "Codex");
        Chapter category = chapterDao.createCodexChapter(codex.getId(), "CHARACTER", "Characters");

        TrashItem trashed = trashService.trashChapter(TEST_USER_ID, category.getId()).orElseThrow();
        assertEquals("CODEX_CATEGORY", trashed.getRootType());

        TrashItem restored = trashService.restore(TEST_USER_ID, trashed.getBatchId());
        assertNotNull(restored);
        assertTrue(chapterDao.findById(category.getId()).isPresent());
    }

    @Test
    void restore_codexEntry_parentCategoryLive_succeeds() throws SQLException {
        var codex = codexDao.createForProject(project.getId(), "Codex");
        Chapter category = chapterDao.createCodexChapter(codex.getId(), "NOTES", "Notes");
        Scene entry = sceneDao.create(category.getId(), "Entry One", null);

        TrashItem trashed = trashService.trashScene(TEST_USER_ID, entry.getId()).orElseThrow();
        assertEquals("CODEX_ENTRY", trashed.getRootType());

        trashService.restore(TEST_USER_ID, trashed.getBatchId());
        assertTrue(sceneDao.findById(entry.getId()).isPresent());
    }

    // =========================================================================
    // Restore — 404 for unknown or other user's batch
    // =========================================================================

    @Test
    void restore_unknownBatchId_throws404() {
        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, UUID.randomUUID()));
        assertEquals(404, ex.status());
    }

    @Test
    void restore_otherUsersBatch_throws404() throws SQLException {
        TrashItem trashed = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();

        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.restore(OTHER_USER_ID, trashed.getBatchId()));
        assertEquals(404, ex.status());
    }

    // =========================================================================
    // Restore removes batch
    // =========================================================================

    @Test
    void restore_removesBatchAfterSuccess() throws SQLException {
        TrashItem trashed = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();

        trashService.restore(TEST_USER_ID, trashed.getBatchId());

        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());
    }

    // =========================================================================
    // Purge
    // =========================================================================

    @Test
    void purge_removesBatchAndHardDeletesRoot() throws SQLException {
        TrashItem trashed = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();

        trashService.purge(TEST_USER_ID, trashed.getBatchId());

        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());
        assertFalse(rawRowExists("book", book.getId()));
        // Cascade should have removed children
        assertFalse(rawRowExists("chapter", chapter.getId()));
        assertFalse(rawRowExists("scene", scene.getId()));
    }

    @Test
    void purge_sweepsOrphanedChildBatches() throws SQLException {
        // Trash the scene separately, then the book
        trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();
        TrashItem trashedBook = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();

        assertEquals(2, trashDao.listForUser(TEST_USER_ID).size());

        // Purge the book — the scene is cascade-deleted, its batch is orphaned
        trashService.purge(TEST_USER_ID, trashedBook.getBatchId());

        // Both batches should be gone (book's explicitly, scene's via orphan sweep)
        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());
    }

    @Test
    void purge_unknownBatchId_throws404() {
        TrashException ex = assertThrows(TrashException.class,
                () -> trashService.purge(TEST_USER_ID, UUID.randomUUID()));
        assertEquals(404, ex.status());
    }

    // =========================================================================
    // Empty
    // =========================================================================

    @Test
    void empty_purgesAllItemsAndReturnsCount() throws SQLException {
        trashService.trashScene(TEST_USER_ID, scene.getId());
        trashService.trashChapter(TEST_USER_ID, chapter.getId());
        trashService.trashBook(TEST_USER_ID, book.getId());

        int purged = trashService.empty(TEST_USER_ID);

        assertEquals(3, purged);
        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());
        // The project should still exist (we didn't trash it)
        assertTrue(projectDao.findByIdForUser(project.getId(), TEST_USER_ID).isPresent());
    }

    @Test
    void empty_doesNotAffectOtherUsers() throws SQLException {
        trashService.trashScene(TEST_USER_ID, scene.getId());

        Project otherProject = createTestProject(OTHER_USER_ID, "Other", null);
        Book otherBook = bookDao.create(otherProject.getId(), "Other Book", null, null, null);
        trashService.trashBook(OTHER_USER_ID, otherBook.getId());

        trashService.empty(TEST_USER_ID);

        // Other user's trash should be untouched
        assertEquals(1, trashDao.listForUser(OTHER_USER_ID).size());
    }

    @Test
    void empty_noItems_returnsZero() throws SQLException {
        int purged = trashService.empty(TEST_USER_ID);
        assertEquals(0, purged);
    }

    // =========================================================================
    // Full lifecycle
    // =========================================================================

    @Test
    void fullLifecycle_trashRestoreTrashPurge() throws SQLException {
        // Trash
        TrashItem trashed = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();
        assertFalse(sceneDao.findById(scene.getId()).isPresent());

        // Restore
        trashService.restore(TEST_USER_ID, trashed.getBatchId());
        assertTrue(sceneDao.findById(scene.getId()).isPresent());
        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());

        // Trash again (second time — confirms restored item is trashable again)
        TrashItem trashed2 = trashService.trashScene(TEST_USER_ID, scene.getId()).orElseThrow();
        assertFalse(sceneDao.findById(scene.getId()).isPresent());

        // Purge
        trashService.purge(TEST_USER_ID, trashed2.getBatchId());
        assertFalse(rawRowExists("scene", scene.getId()));
        assertTrue(trashDao.listForUser(TEST_USER_ID).isEmpty());
    }

    @Test
    void restore_afterParentRestored_succeeds() throws SQLException {
        // Trash chapter, then book (chapter's parent)
        TrashItem trashedChapter = trashService.trashChapter(TEST_USER_ID, chapter.getId()).orElseThrow();
        TrashItem trashedBook = trashService.trashBook(TEST_USER_ID, book.getId()).orElseThrow();

        // Restoring the chapter first should fail (parent is trashed)
        assertThrows(TrashException.class,
                () -> trashService.restore(TEST_USER_ID, trashedChapter.getBatchId()));

        // Restore the book first
        trashService.restore(TEST_USER_ID, trashedBook.getBatchId());

        // Now the chapter can be restored
        trashService.restore(TEST_USER_ID, trashedChapter.getBatchId());
        assertTrue(chapterDao.findById(chapter.getId()).isPresent());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean rawRowExists(String table, UUID id) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
