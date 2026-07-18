package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@ExtendWith(DropwizardExtensionsSupport.class)
class SceneResourceTest extends NovelKmsTestBase {

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new SceneResource(sceneDao, trashService))
            .setMapper(createMapper())
            .build();

    private Project testProject;
    private Book    testBook;
    private Chapter testChapter;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        testProject = createTestProject("Test Project", null);;
        testBook    = bookDao.create(testProject.getId(), "Test Book", null, null, null);
        testChapter = chapterDao.create(testBook.getId(), null, "Test Chapter", null, null);
    }

    // -------------------------------------------------------------------------
    // GET /chapters/{chapterId}/scenes
    // -------------------------------------------------------------------------

    @Test
    void listScenes_empty_returns200AndEmptyArray() {
        Response r = RESOURCES.target("/chapters/" + testChapter.getId() + "/scenes")
                .request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Scene> scenes = r.readEntity(new GenericType<>() {});
        assertEquals(0, scenes.size());
    }

    @Test
    void listScenes_returnsScenesForChapter() throws SQLException {
        sceneDao.create(testChapter.getId(), "Scene A", null);
        sceneDao.create(testChapter.getId(), "Scene B", null);

        Response r = RESOURCES.target("/chapters/" + testChapter.getId() + "/scenes")
                .request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Scene> scenes = r.readEntity(new GenericType<>() {});
        assertEquals(2, scenes.size());
    }

    // -------------------------------------------------------------------------
    // GET /scenes/{id}
    // -------------------------------------------------------------------------

    @Test
    void getScene_knownId_returns200() throws SQLException {
        Scene s = sceneDao.create(testChapter.getId(), "My Scene", "my notes");

        Response r = RESOURCES.target("/scenes/" + s.getId()).request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Scene found = r.readEntity(Scene.class);
        assertEquals("My Scene", found.getTitle());
        assertEquals("my notes", found.getNotes());
        assertNull(found.getContent());
        assertEquals(0, found.getWordCount());
    }

    @Test
    void getScene_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/scenes/" + UUID.randomUUID()).request().get();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    // -------------------------------------------------------------------------
    // POST /chapters/{chapterId}/scenes
    // -------------------------------------------------------------------------

    @Test
    void createScene_validRequest_returns201() {
        Response r = RESOURCES.target("/chapters/" + testChapter.getId() + "/scenes")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("title", "New Scene", "notes", "opening scene")));

        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());
        Scene created = r.readEntity(Scene.class);
        assertNotNull(created.getId());
        assertEquals("New Scene", created.getTitle());
        assertEquals(testChapter.getId(), created.getChapterId());
        assertNull(created.getContent());
        assertEquals(0, created.getWordCount());
    }

    @Test
    void createScene_missingTitle_returns400() {
        Response r = RESOURCES.target("/chapters/" + testChapter.getId() + "/scenes")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("notes", "No title")));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    // -------------------------------------------------------------------------
    // PUT /scenes/{id}  (metadata update)
    // -------------------------------------------------------------------------

    @Test
    void updateScene_knownId_returns200() throws SQLException {
        Scene s = sceneDao.create(testChapter.getId(), "Old Title", null);

        Response r = RESOURCES.target("/scenes/" + s.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "New Title", "notes", "Updated")));

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Scene updated = r.readEntity(Scene.class);
        assertEquals("New Title", updated.getTitle());
        assertEquals("Updated", updated.getNotes());
    }

    @Test
    void updateScene_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/scenes/" + UUID.randomUUID())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Ghost")));

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    // -------------------------------------------------------------------------
    // PUT /scenes/{id}/content  (TipTap content save)
    // -------------------------------------------------------------------------

    @Test
    void splitScene_validRequest_returns201AndPersistsBothScenes() throws SQLException {
        Scene source = sceneDao.create(testChapter.getId(), "Source", null);

        Response r = RESOURCES.target("/scenes/" + source.getId() + "/split")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of(
                        "title", "New Scene [abcd]",
                        "beforeContent", "<p>Before</p>",
                        "beforeWordCount", 1,
                        "afterContent", "<p>After</p>",
                        "afterWordCount", 1)));

        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());
        Scene created = r.readEntity(Scene.class);
        assertEquals("New Scene [abcd]", created.getTitle());
        assertEquals("<p>After</p>", created.getContent());

        Scene updatedSource = sceneDao.findById(source.getId()).orElseThrow();
        assertEquals("<p>Before</p>", updatedSource.getContent());
        assertEquals(2, sceneDao.findByChapterId(testChapter.getId()).size());
    }

    @Test
    void splitScene_unknownSource_returnsNoContent() {
        Response r = RESOURCES.target("/scenes/" + UUID.randomUUID() + "/split")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of(
                        "title", "New Scene [abcd]",
                        "beforeContent", "<p>Before</p>",
                        "beforeWordCount", 1,
                        "afterContent", "<p>After</p>",
                        "afterWordCount", 1)));

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void saveContent_validRequest_returns200WithContent() throws SQLException {
        Scene s = sceneDao.create(testChapter.getId(), "Draft", null);
        String tiptapJson = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

        Response r = RESOURCES.target("/scenes/" + s.getId() + "/content")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("content", tiptapJson, "wordCount", 15)));

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Scene saved = r.readEntity(Scene.class);
        assertEquals(tiptapJson, saved.getContent());
        assertEquals(15, saved.getWordCount());
    }

    @Test
    void saveContent_unknownId_returnsNoContent() {
        String json = "{\"type\":\"doc\"}";

        Response r = RESOURCES.target("/scenes/" + UUID.randomUUID() + "/content")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("content", json, "wordCount", 0)));

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void saveContent_doesNotAffectTitle() throws SQLException {
        Scene s = sceneDao.create(testChapter.getId(), "Stable Title", null);

        RESOURCES.target("/scenes/" + s.getId() + "/content")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("content", "{\"type\":\"doc\"}", "wordCount", 5)));

        Response r = RESOURCES.target("/scenes/" + s.getId()).request().get();
        Scene found = r.readEntity(Scene.class);
        assertEquals("Stable Title", found.getTitle());
    }

    // -------------------------------------------------------------------------
    // DELETE /scenes/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteScene_knownId_returnsOk() throws SQLException {
        Scene s = sceneDao.create(testChapter.getId(), "To Delete", null);

        Response r = RESOURCES.target("/scenes/" + s.getId()).request().delete();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
    }

    @Test
    void deleteScene_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/scenes/" + UUID.randomUUID()).request().delete();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }
}
