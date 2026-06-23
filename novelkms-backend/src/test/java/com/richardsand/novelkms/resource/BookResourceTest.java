package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Project;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
class BookResourceTest extends NovelKmsTestBase {

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new BookResource(bookDao, null))
            .setMapper(createMapper())
            .build();

    private Project testProject;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        testProject = createTestProject("Test Project", null);
    }

    // -------------------------------------------------------------------------
    // GET /api/projects/{projectId}/books
    // -------------------------------------------------------------------------

    @Test
    void listBooks_empty_returns200AndEmptyArray() {
        Response r = RESOURCES.target("/projects/" + testProject.getId() + "/books")
                .request().get();

        assertEquals(200, r.getStatus());
        List<Book> books = r.readEntity(new GenericType<>() {});
        assertEquals(0, books.size());
    }

    @Test
    void listBooks_returnsBooksForProject() throws SQLException {
        bookDao.create(testProject.getId(), "Book A", null, null, null);
        bookDao.create(testProject.getId(), "Book B", null, null, null);

        Response r = RESOURCES.target("/projects/" + testProject.getId() + "/books")
                .request().get();

        assertEquals(200, r.getStatus());
        List<Book> books = r.readEntity(new GenericType<>() {});
        assertEquals(2, books.size());
    }

    // -------------------------------------------------------------------------
    // GET /api/books/{id}
    // -------------------------------------------------------------------------

    @Test
    void getBook_knownId_returns200() throws SQLException {
        Book b = bookDao.create(testProject.getId(), "My Book", "Sub", null, null);

        Response r = RESOURCES.target("/books/" + b.getId()).request().get();

        assertEquals(200, r.getStatus());
        Book found = r.readEntity(Book.class);
        assertEquals("My Book", found.getTitle());
        assertEquals("Sub", found.getSubtitle());
    }

    @Test
    void getBook_unknownId_returns404() {
        Response r = RESOURCES.target("/books/" + UUID.randomUUID()).request().get();

        assertEquals(404, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // POST /api/projects/{projectId}/books
    // -------------------------------------------------------------------------

    @Test
    void createBook_validRequest_returns201() {
        Response r = RESOURCES.target("/projects/" + testProject.getId() + "/books")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("title", "New Book", "subtitle", "A Subtitle")));

        assertEquals(201, r.getStatus());
        Book created = r.readEntity(Book.class);
        assertNotNull(created.getId());
        assertEquals("New Book", created.getTitle());
        assertEquals(testProject.getId(), created.getProjectId());
        assertEquals(0, created.getDisplayOrder());
    }

    @Test
    void createBook_missingTitle_returns400() {
        Response r = RESOURCES.target("/projects/" + testProject.getId() + "/books")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("subtitle", "No title")));

        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // PUT /api/books/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateBook_knownId_returns200() throws SQLException {
        Book b = bookDao.create(testProject.getId(), "Old Title", null, null, null);

        Response r = RESOURCES.target("/books/" + b.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "New Title", "notes", "Updated notes")));

        assertEquals(200, r.getStatus());
        Book updated = r.readEntity(Book.class);
        assertEquals("New Title", updated.getTitle());
        assertEquals("Updated notes", updated.getNotes());
    }

    @Test
    void updateBook_unknownId_returns404() {
        Response r = RESOURCES.target("/books/" + UUID.randomUUID())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Ghost")));

        assertEquals(404, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/books/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteBook_knownId_returns204() throws SQLException {
        Book b = bookDao.create(testProject.getId(), "To Delete", null, null, null);

        Response r = RESOURCES.target("/books/" + b.getId()).request().delete();

        assertEquals(204, r.getStatus());
    }

    @Test
    void deleteBook_unknownId_returns404() {
        Response r = RESOURCES.target("/books/" + UUID.randomUUID()).request().delete();

        assertEquals(404, r.getStatus());
    }
}
