package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;

class KmsArchiveServiceTest extends NovelKmsTestBase {

    private KmsArchiveService service = new KmsArchiveService(ds);

    @Test
    void exportPreviewAndImportProjectArchiveAsNewProject() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection c = ds.getConnection()) {
            insertProject(c, projectId, ownerA, "Source Project", now);
            insertBook(c, bookId, projectId, "Source Book", now);
            insertChapter(c, chapterId, bookId, "Chapter One", now);
            insertScene(c, sceneId, chapterId, "Opening", "<p>Hello archive.</p>", 2, now);
        }

        KmsArchiveService.ExportMeta export = service.exportProject(ownerA, projectId);
        assertTrue(export.filename().endsWith("-novelkms.json"));
        assertTrue(export.bytes().length > 0);

        KmsArchiveService.ImportPreview preview = service.preview(new ByteArrayInputStream(export.bytes()));
        assertTrue(preview.valid(), () -> String.join("; ", preview.errors()));
        assertEquals(1, preview.projectCount());
        assertEquals(1, preview.bookCount());
        assertEquals(1, preview.chapterCount());
        assertEquals(1, preview.sceneCount());

        KmsArchiveService.ImportResult result = service.importAsNewProjects(ownerB, new ByteArrayInputStream(export.bytes()));
        assertEquals(1, result.projectCount());
        assertEquals(1, result.projectIds().size());
        UUID importedProjectId = result.projectIds().get(0);
        assertNotEquals(projectId, importedProjectId);

        try (Connection c = ds.getConnection()) {
            assertEquals(1, count(c, "SELECT COUNT(*) FROM project WHERE owner_user_id = ?", ownerB));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM book WHERE project_id = ?", importedProjectId));

            UUID importedBookId = singleUuid(c, "SELECT id FROM book WHERE project_id = ?", importedProjectId);
            assertNotEquals(bookId, importedBookId);

            UUID importedChapterId = singleUuid(c, "SELECT id FROM chapter WHERE book_id = ?", importedBookId);
            assertNotEquals(chapterId, importedChapterId);

            UUID importedSceneId = singleUuid(c, "SELECT id FROM scene WHERE chapter_id = ?", importedChapterId);
            assertNotEquals(sceneId, importedSceneId);

            try (PreparedStatement ps = c.prepareStatement("SELECT content FROM scene WHERE id = ?")) {
                ps.setObject(1, importedSceneId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("<p>Hello archive.</p>", rs.getString(1));
                    assertFalse(rs.next());
                }
            }
        }
    }

    private void insertProject(Connection c, UUID id, UUID ownerId, String title, Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO project (id, owner_user_id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, title);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertBook(Connection c, UUID id, UUID projectId, String title, Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO book (id, project_id, title, display_order, created_at, updated_at)
                VALUES (?, ?, ?, 0, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setString(3, title);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertChapter(Connection c, UUID id, UUID bookId, String title, Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO chapter (id, book_id, title, display_order, resets_numbering, created_at, updated_at)
                VALUES (?, ?, ?, 0, FALSE, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, bookId);
            ps.setString(3, title);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertScene(Connection c, UUID id, UUID chapterId, String title, String content,
            int wordCount, Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO scene (id, chapter_id, title, display_order, content, word_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, chapterId);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setInt(5, wordCount);
            ps.setTimestamp(6, Timestamp.from(now));
            ps.setTimestamp(7, Timestamp.from(now));
            ps.executeUpdate();
        }
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

    private UUID singleUuid(Connection c, String sql, UUID id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                UUID value = rs.getObject(1, UUID.class);
                assertFalse(rs.next());
                return value;
            }
        }
    }
}
