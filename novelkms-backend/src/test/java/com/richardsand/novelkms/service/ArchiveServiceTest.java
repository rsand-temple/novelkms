package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.ArchiveDao;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;

class ArchiveServiceTest extends NovelKmsTestBase {

    @Test
    void exportPreviewAndImportProjectArchiveAsNewProject() throws Exception {
        truncateAll();

        UUID ownerA = TEST_USER_ID;
        UUID ownerB = OTHER_USER_ID;

        Project project = projectDao.createForUser(ownerA, "Source Project", null);
        Book book = bookDao.create(project.getId(), "Source Book", null, null, null);
        Chapter chapter = chapterDao.create(book.getId(), null, "Chapter One", null, null);
        Scene scene = sceneDao.create(chapter.getId(), "Opening", null);
        sceneDao.saveContent(scene.getId(), "<p>Hello archive.</p>", 2);

        ArchiveService service = new ArchiveService(new ArchiveDao(ds));

        ArchiveService.ExportMeta export = service.exportProject(ownerA, project.getId());
        assertTrue(export.filename().endsWith("-novelkms.json"));
        assertTrue(export.bytes().length > 0);

        String json = new String(export.bytes(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"books\""), json);
        assertTrue(json.contains("Source Book"), json);
        assertTrue(json.contains("<p>Hello archive.</p>"), json);

        ArchiveService.ImportPreview preview = service.preview(new ByteArrayInputStream(export.bytes()));
        assertTrue(preview.valid(), () -> String.join("; ", preview.errors()));
        assertEquals(1, preview.projectCount());
        assertEquals(1, preview.bookCount());
        assertEquals(1, preview.chapterCount());
        assertEquals(1, preview.sceneCount());

        ArchiveService.ImportResult result = service.importAsNewProjects(ownerB, new ByteArrayInputStream(export.bytes()));
        assertEquals(1, result.projectCount());
        assertEquals(1, result.projectIds().size());
        UUID importedProjectId = result.projectIds().get(0);
        assertNotEquals(project.getId(), importedProjectId);

        try (Connection c = ds.getConnection()) {
            assertEquals(1, count(c, "SELECT COUNT(*) FROM project WHERE owner_user_id = ?", ownerA));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM project WHERE owner_user_id = ?", ownerB));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM book WHERE project_id = ?", importedProjectId));

            UUID importedBookId = singleUuid(c, "SELECT id FROM book WHERE project_id = ?", importedProjectId);
            assertNotEquals(book.getId(), importedBookId);

            UUID importedChapterId = singleUuid(c, "SELECT id FROM chapter WHERE book_id = ?", importedBookId);
            assertNotEquals(chapter.getId(), importedChapterId);

            UUID importedSceneId = singleUuid(c, "SELECT id FROM scene WHERE chapter_id = ?", importedChapterId);
            assertNotEquals(scene.getId(), importedSceneId);

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
