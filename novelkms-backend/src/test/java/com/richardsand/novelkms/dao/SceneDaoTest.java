package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;

class SceneDaoTest extends NovelKmsTestBase {

    private Project project;
    private Book    book;
    private Chapter chapter;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = projectDao.create("Test Project", null);
        book = bookDao.create(project.getId(), "Test Book", null, null, null);
        chapter = chapterDao.create(book.getId(), null, "Test Chapter", null, null);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_newScene_hasNullContentAndZeroWordCount() throws SQLException {
        Scene s = sceneDao.create(chapter.getId(), "Scene One", "notes");

        assertNotNull(s.getId());
        assertEquals(chapter.getId(), s.getChapterId());
        assertEquals("Scene One", s.getTitle());
        assertNull(s.getContent());
        assertEquals(0, s.getWordCount());
        assertEquals(0, s.getDisplayOrder());
    }

    @Test
    void create_displayOrder_incrementsPerChapter() throws SQLException {
        Scene s1 = sceneDao.create(chapter.getId(), "Scene 1", null);
        Scene s2 = sceneDao.create(chapter.getId(), "Scene 2", null);
        Scene s3 = sceneDao.create(chapter.getId(), "Scene 3", null);

        assertEquals(0, s1.getDisplayOrder());
        assertEquals(1, s2.getDisplayOrder());
        assertEquals(2, s3.getDisplayOrder());
    }

    @Test
    void create_displayOrder_isIndependentPerChapter() throws SQLException {
        Chapter other = chapterDao.create(book.getId(), null, "Other Chapter", null, null);

        sceneDao.create(chapter.getId(), "Ch1 Scene 1", null);
        sceneDao.create(chapter.getId(), "Ch1 Scene 2", null);
        Scene s = sceneDao.create(other.getId(), "Ch2 Scene 1", null);

        assertEquals(0, s.getDisplayOrder());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_returnsScene() throws SQLException {
        Scene created = sceneDao.create(chapter.getId(), "Persisted", null);

        Optional<Scene> found = sceneDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals("Persisted", found.get().getTitle());
    }

    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Scene> found = sceneDao.findById(UUID.randomUUID());

        assertFalse(found.isPresent());
    }

    // -------------------------------------------------------------------------
    // findByChapterId
    // -------------------------------------------------------------------------

    @Test
    void findByChapterId_empty_returnsEmptyList() throws SQLException {
        List<Scene> scenes = sceneDao.findByChapterId(chapter.getId());

        assertTrue(scenes.isEmpty());
    }

    @Test
    void findByChapterId_returnsScenesInOrder() throws SQLException {
        sceneDao.create(chapter.getId(), "Scene A", null);
        sceneDao.create(chapter.getId(), "Scene B", null);
        sceneDao.create(chapter.getId(), "Scene C", null);

        List<Scene> scenes = sceneDao.findByChapterId(chapter.getId());

        assertEquals(3, scenes.size());
        assertEquals("Scene A", scenes.get(0).getTitle());
        assertEquals("Scene B", scenes.get(1).getTitle());
        assertEquals("Scene C", scenes.get(2).getTitle());
    }

    @Test
    void findByChapterId_doesNotReturnScenesFromOtherChapters() throws SQLException {
        Chapter other = chapterDao.create(book.getId(), null, "Other Chapter", null, null);
        sceneDao.create(chapter.getId(), "My Scene", null);
        sceneDao.create(other.getId(), "Other Scene", null);

        List<Scene> scenes = sceneDao.findByChapterId(chapter.getId());

        assertEquals(1, scenes.size());
        assertEquals("My Scene", scenes.get(0).getTitle());
    }

    // -------------------------------------------------------------------------
    // saveContent
    // -------------------------------------------------------------------------

    @Test
    void split_updatesSourceAndInsertsNewSceneImmediatelyAfter() throws SQLException {
        Scene first  = sceneDao.create(chapter.getId(), "First", null);
        Scene source = sceneDao.create(chapter.getId(), "Source", null);
        Scene last   = sceneDao.create(chapter.getId(), "Last", null);
        sceneDao.saveContent(source.getId(), "<p>Before After</p>", 2);

        Scene created = sceneDao.split(
                source.getId(),
                "New Scene [abcd]",
                "<p>Before</p>",
                1,
                "<p>After</p>",
                1)
                .orElseThrow();

        Scene updatedSource = sceneDao.findById(source.getId()).orElseThrow();
        assertEquals("<p>Before</p>", updatedSource.getContent());
        assertEquals(1, updatedSource.getWordCount());
        assertEquals("New Scene [abcd]", created.getTitle());
        assertEquals("<p>After</p>", created.getContent());
        assertEquals(1, created.getWordCount());

        List<Scene> ordered = sceneDao.findByChapterId(chapter.getId());
        assertEquals(List.of(first.getId(), source.getId(), created.getId(), last.getId()),
                ordered.stream().map(Scene::getId).toList());
        assertEquals(List.of(0, 1, 2, 3),
                ordered.stream().map(Scene::getDisplayOrder).toList());
    }

    @Test
    void split_unknownSource_returnsEmptyAndCreatesNothing() throws SQLException {
        Optional<Scene> result = sceneDao.split(
                UUID.randomUUID(),
                "New Scene [abcd]",
                "<p>Before</p>",
                1,
                "<p>After</p>",
                1);

        assertTrue(result.isEmpty());
        assertTrue(sceneDao.findByChapterId(chapter.getId()).isEmpty());
    }

    @Test
    void saveContent_storesJsonAndWordCount() throws SQLException {
        Scene s = sceneDao.create(chapter.getId(), "Draft Scene", null);

        String          tiptapJson = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";
        Optional<Scene> saved      = sceneDao.saveContent(s.getId(), tiptapJson, 42);

        assertTrue(saved.isPresent());
        assertEquals(tiptapJson, saved.get().getContent());
        assertEquals(42, saved.get().getWordCount());
    }

    @Test
    void saveContent_canOverwriteExistingContent() throws SQLException {
        Scene s = sceneDao.create(chapter.getId(), "Scene", null);
        sceneDao.saveContent(s.getId(), "{\"type\":\"doc\"}", 5);

        String          newContent = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[]}]}";
        Optional<Scene> updated    = sceneDao.saveContent(s.getId(), newContent, 100);

        assertTrue(updated.isPresent());
        assertEquals(newContent, updated.get().getContent());
        assertEquals(100, updated.get().getWordCount());
    }

    @Test
    void saveContent_unknownId_returnsEmpty() throws SQLException {
        Optional<Scene> saved = sceneDao.saveContent(UUID.randomUUID(), "{}", 0);

        assertFalse(saved.isPresent());
    }

    // -------------------------------------------------------------------------
    // update (metadata)
    // -------------------------------------------------------------------------

    @Test
    void update_changesTitleAndNotes() throws SQLException {
        Scene original = sceneDao.create(chapter.getId(), "Old Title", "Old Notes");

        Optional<Scene> updated = sceneDao.update(original.getId(), "New Title", "New Notes");

        assertTrue(updated.isPresent());
        assertEquals("New Title", updated.get().getTitle());
        assertEquals("New Notes", updated.get().getNotes());
    }

    @Test
    void update_doesNotClobberContent() throws SQLException {
        Scene  s       = sceneDao.create(chapter.getId(), "Scene", null);
        String content = "{\"type\":\"doc\"}";
        sceneDao.saveContent(s.getId(), content, 10);

        sceneDao.update(s.getId(), "Renamed Scene", null);
        Optional<Scene> found = sceneDao.findById(s.getId());

        assertTrue(found.isPresent());
        assertEquals(content, found.get().getContent());
        assertEquals(10, found.get().getWordCount());
    }

    @Test
    void update_unknownId_returnsEmpty() throws SQLException {
        Optional<Scene> updated = sceneDao.update(UUID.randomUUID(), "Ghost", null);

        assertFalse(updated.isPresent());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesScene() throws SQLException {
        Scene s = sceneDao.create(chapter.getId(), "To Delete", null);

        boolean deleted = sceneDao.delete(s.getId());

        assertTrue(deleted);
        assertFalse(sceneDao.findById(s.getId()).isPresent());
    }

    @Test
    void delete_chapter_cascadesToScenes() throws SQLException {
        Scene s = sceneDao.create(chapter.getId(), "Cascaded", null);

        chapterDao.delete(chapter.getId());

        assertFalse(sceneDao.findById(s.getId()).isPresent());
    }
}
