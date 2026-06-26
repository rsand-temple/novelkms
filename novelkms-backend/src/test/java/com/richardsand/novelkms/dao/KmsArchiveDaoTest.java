package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;

class KmsArchiveDaoTest extends NovelKmsTestBase {

    @Test
    void exportSelectorsReturnLowercaseColumnsAndExpectedRows() throws Exception {
        truncateAll();

        KmsArchiveDao dao     = new KmsArchiveDao(ds);
        Project       project = projectDao.createForUser(TEST_USER_ID, "Source Project", null);
        Book          book    = bookDao.create(project.getId(), "Source Book", null, null, null);
        Chapter       chapter = chapterDao.create(book.getId(), null, "Chapter One", null, null);
        Scene         scene   = sceneDao.create(chapter.getId(), "Opening", null);
        sceneDao.saveContent(scene.getId(), "<p>Hello archive.</p>", 2);

        List<Map<String, Object>> projects = dao.findProjectForExport(TEST_USER_ID, project.getId());
        assertEquals(1, projects.size());
        assertTrue(projects.get(0).containsKey("id"));
        assertFalse(projects.get(0).containsKey("ID"));

        List<Map<String, Object>> books = dao.findBooksForProject(project.getId());
        assertEquals(1, books.size());
        assertEquals(book.getId().toString(), books.get(0).get("id"));
        assertTrue(books.get(0).containsKey("project_id"));

        List<Map<String, Object>> chapters = dao.findChaptersForProject(project.getId());
        assertEquals(1, chapters.size());
        assertEquals(chapter.getId().toString(), chapters.get(0).get("id"));
        assertTrue(chapters.get(0).containsKey("book_id"));

        List<Map<String, Object>> scenes = dao.findScenesForChapters(List.of(chapter.getId()));
        assertEquals(1, scenes.size());
        assertEquals(scene.getId().toString(), scenes.get(0).get("id"));
        assertEquals("<p>Hello archive.</p>", scenes.get(0).get("content"));
    }

    @Test
    void insertRowsInTransactionPreservesDestinationOwnerAndRelationships() throws Exception {
        truncateAll();

        KmsArchiveDao dao = new KmsArchiveDao(ds);

        UUID   newProjectId = UUID.randomUUID();
        UUID   newBookId    = UUID.randomUUID();
        UUID   newChapterId = UUID.randomUUID();
        UUID   newSceneId   = UUID.randomUUID();
        String now          = Instant.now().toString();

        List<KmsArchiveDao.InsertBatch> batches = new ArrayList<>();
        batches.add(new KmsArchiveDao.InsertBatch("project", List.of(row(
                "id", newProjectId.toString(),
                "owner_user_id", OTHER_USER_ID.toString(),
                "title", "Imported Project",
                "created_at", now,
                "updated_at", now))));
        batches.add(new KmsArchiveDao.InsertBatch("book", List.of(row(
                "id", newBookId.toString(),
                "project_id", newProjectId.toString(),
                "title", "Imported Book",
                "display_order", 0,
                "created_at", now,
                "updated_at", now))));
        batches.add(new KmsArchiveDao.InsertBatch("chapter", List.of(row(
                "id", newChapterId.toString(),
                "book_id", newBookId.toString(),
                "title", "Imported Chapter",
                "display_order", 0,
                "resets_numbering", false,
                "created_at", now,
                "updated_at", now))));
        batches.add(new KmsArchiveDao.InsertBatch("scene", List.of(row(
                "id", newSceneId.toString(),
                "chapter_id", newChapterId.toString(),
                "title", "Imported Scene",
                "display_order", 0,
                "content", "<p>Hello archive.</p>",
                "word_count", 2,
                "created_at", now,
                "updated_at", now))));

        List<String> warnings = new ArrayList<>();
        dao.insertRowsInTransaction(batches, warnings);
        assertTrue(warnings.isEmpty(), () -> String.join("; ", warnings));

        try (Connection c = ds.getConnection()) {
            assertEquals(1, count(c, "SELECT COUNT(*) FROM project WHERE owner_user_id = ?", OTHER_USER_ID));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM book WHERE project_id = ?", newProjectId));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM chapter WHERE book_id = ?", newBookId));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM scene WHERE chapter_id = ?", newChapterId));
        }
    }

    private Map<String, Object> row(Object... values) {
        assertEquals(0, values.length % 2);
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        assertNotNull(row.get("id"));
        return row;
    }

    private int count(Connection c, String sql, UUID id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }
}
