package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
class ChapterResourceTest extends NovelKmsTestBase {

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new ChapterResource(chapterDao, sceneDao, trashService))
            .setMapper(createMapper())
            .build();

    private Project testProject;
    private Book    testBook;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        testProject = createTestProject("Test Project", null);
        testBook = bookDao.create(testProject.getId(), "Test Book", null, null, null);
    }

    // -------------------------------------------------------------------------
    // GET /books/{bookId}/chapters
    // -------------------------------------------------------------------------

    @Test
    void listChapters_empty_returns200AndEmptyArray() {
        Response r = RESOURCES.target("/books/" + testBook.getId() + "/chapters")
                .request().get();

        assertEquals(200, r.getStatus());
        List<Chapter> chapters = r.readEntity(new GenericType<>() {
        });
        assertEquals(0, chapters.size());
    }

    @Test
    void listChapters_returnsChaptersForBook() throws SQLException {
        chapterDao.create(testBook.getId(), null, "Chapter A", null, null);
        chapterDao.create(testBook.getId(), null, "Chapter B", null, null);

        Response r = RESOURCES.target("/books/" + testBook.getId() + "/chapters")
                .request().get();

        assertEquals(200, r.getStatus());
        List<Chapter> chapters = r.readEntity(new GenericType<>() {
        });
        assertEquals(2, chapters.size());
    }

    // -------------------------------------------------------------------------
    // GET /chapters/{id}
    // -------------------------------------------------------------------------

    @Test
    void getChapter_knownId_returns200() throws SQLException {
        Chapter ch = chapterDao.create(testBook.getId(), null, "My Chapter", null, "notes");

        Response r = RESOURCES.target("/chapters/" + ch.getId()).request().get();

        assertEquals(200, r.getStatus());
        Chapter found = r.readEntity(Chapter.class);
        assertEquals("My Chapter", found.getTitle());
        assertEquals("notes", found.getNotes());
        assertNull(found.getPartId());
    }

    @Test
    void getChapter_unknownId_returns404() {
        Response r = RESOURCES.target("/chapters/" + UUID.randomUUID()).request().get();

        assertEquals(404, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // POST /books/{bookId}/chapters
    // -------------------------------------------------------------------------

    @Test
    void createChapter_withoutPart_returns201() {
        Response r = RESOURCES.target("/books/" + testBook.getId() + "/chapters")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("title", "New Chapter")));

        assertEquals(201, r.getStatus());
        Chapter created = r.readEntity(Chapter.class);
        assertNotNull(created.getId());
        assertEquals("New Chapter", created.getTitle());
        assertEquals(testBook.getId(), created.getBookId());
        assertNull(created.getPartId());
    }

    @Test
    void createChapter_missingTitle_returns400() {
        Response r = RESOURCES.target("/books/" + testBook.getId() + "/chapters")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("notes", "No title")));

        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // PUT /chapters/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateChapter_knownId_returns200() throws SQLException {
        Chapter ch = chapterDao.create(testBook.getId(), null, "Old Title", null, null);

        Response r = RESOURCES.target("/chapters/" + ch.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "title", "New Title",
                        "notes", "New notes",
                        "resetsNumbering", false)));

        assertEquals(200, r.getStatus());
        Chapter updated = r.readEntity(Chapter.class);
        assertEquals("New Title", updated.getTitle());
        assertEquals("New notes", updated.getNotes());
    }

    @Test
    void updateChapter_unknownId_returns404() {
        Response r = RESOURCES.target("/chapters/" + UUID.randomUUID())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "title", "Ghost",
                        "resetsNumbering", false)));

        assertEquals(404, r.getStatus());
    }

    @Test
    void updateChapter_missingResetsNumbering_returns400() throws SQLException {
        // resetsNumbering is intentionally boxed (Boolean, not boolean) and required
        // on every update — a caller that omits it fails loudly instead of silently
        // clearing the flag (the same bug class fixed earlier for scene word counts).
        Chapter ch = chapterDao.create(testBook.getId(), null, "Old Title", null, null);

        Response r = RESOURCES.target("/chapters/" + ch.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "New Title", "notes", "New notes")));

        assertEquals(400, r.getStatus());
    }

    @Test
    void updateChapter_blankTitle_returns200AndPersistsBlank() throws SQLException {
        // A blank title is a valid update state — the nav tree and EditorPanel both
        // fall back to "Chapter N" display. Only create requires a non-blank title.
        Chapter ch = chapterDao.create(testBook.getId(), null, "Old Title", null, null);

        Response r = RESOURCES.target("/chapters/" + ch.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "title", "",
                        "resetsNumbering", false)));

        assertEquals(200, r.getStatus());
        Chapter updated = r.readEntity(Chapter.class);
        assertEquals("", updated.getTitle());
    }

    @Test
    void updateChapter_setsResetsNumbering_persistsValue() throws SQLException {
        Chapter ch = chapterDao.create(testBook.getId(), null, "Old Title", null, null);

        Response r = RESOURCES.target("/chapters/" + ch.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "title", "Old Title",
                        "resetsNumbering", true)));

        assertEquals(200, r.getStatus());
        Chapter updated = r.readEntity(Chapter.class);
        assertTrue(updated.isResetsNumbering());
    }

    // -------------------------------------------------------------------------
    // DELETE /chapters/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteChapter_knownId_returns204() throws SQLException {
        Chapter ch = chapterDao.create(testBook.getId(), null, "To Delete", null, null);

        Response r = RESOURCES.target("/chapters/" + ch.getId()).request().delete();

        assertEquals(204, r.getStatus());
    }

    @Test
    void deleteChapter_unknownId_returns404() {
        Response r = RESOURCES.target("/chapters/" + UUID.randomUUID()).request().delete();

        assertEquals(404, r.getStatus());
    }
}