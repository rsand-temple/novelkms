package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Project;

class BookDaoTest extends NovelKmsTestBase {

    private Project project;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = projectDao.create("Test Project", null);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_assignsIdAndLinksToProject() throws SQLException {
        Book b = bookDao.create(project.getId(), "Book One", "A Subtitle", "Some notes");

        assertNotNull(b.getId());
        assertEquals(project.getId(), b.getProjectId());
        assertEquals("Book One", b.getTitle());
        assertEquals("A Subtitle", b.getSubtitle());
        assertEquals("Some notes", b.getNotes());
        assertEquals(0, b.getDisplayOrder());
    }

    @Test
    void create_displayOrder_incrementsPerProject() throws SQLException {
        Book b1 = bookDao.create(project.getId(), "First", null, null);
        Book b2 = bookDao.create(project.getId(), "Second", null, null);
        Book b3 = bookDao.create(project.getId(), "Third", null, null);

        assertEquals(0, b1.getDisplayOrder());
        assertEquals(1, b2.getDisplayOrder());
        assertEquals(2, b3.getDisplayOrder());
    }

    @Test
    void create_displayOrder_isIndependentPerProject() throws SQLException {
        Project other = projectDao.create("Other Project", null);

        bookDao.create(project.getId(), "P1 Book 1", null, null);
        bookDao.create(project.getId(), "P1 Book 2", null, null);
        Book b = bookDao.create(other.getId(), "P2 Book 1", null, null);

        // Should start at 0 for the new project, not continue from the first
        assertEquals(0, b.getDisplayOrder());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_returnsBook() throws SQLException {
        Book created = bookDao.create(project.getId(), "Persisted", null, null);

        Optional<Book> found = bookDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals("Persisted", found.get().getTitle());
    }

    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Book> found = bookDao.findById(UUID.randomUUID());

        assertFalse(found.isPresent());
    }

    // -------------------------------------------------------------------------
    // findByProjectId
    // -------------------------------------------------------------------------

    @Test
    void findByProjectId_empty_returnsEmptyList() throws SQLException {
        List<Book> books = bookDao.findByProjectId(project.getId());

        assertTrue(books.isEmpty());
    }

    @Test
    void findByProjectId_returnsBooksInDisplayOrder() throws SQLException {
        bookDao.create(project.getId(), "Book A", null, null);
        bookDao.create(project.getId(), "Book B", null, null);
        bookDao.create(project.getId(), "Book C", null, null);

        List<Book> books = bookDao.findByProjectId(project.getId());

        assertEquals(3, books.size());
        assertEquals("Book A", books.get(0).getTitle());
        assertEquals("Book B", books.get(1).getTitle());
        assertEquals("Book C", books.get(2).getTitle());
    }

    @Test
    void findByProjectId_doesNotReturnBooksFromOtherProjects() throws SQLException {
        Project other = projectDao.create("Other", null);
        bookDao.create(project.getId(), "My Book", null, null);
        bookDao.create(other.getId(), "Not My Book", null, null);

        List<Book> books = bookDao.findByProjectId(project.getId());

        assertEquals(1, books.size());
        assertEquals("My Book", books.get(0).getTitle());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesMetadata() throws SQLException {
        Book original = bookDao.create(project.getId(), "Old Title", null, null);

        Optional<Book> updated = bookDao.update(original.getId(), "New Title", "New Sub", "New Notes");

        assertTrue(updated.isPresent());
        assertEquals("New Title", updated.get().getTitle());
        assertEquals("New Sub", updated.get().getSubtitle());
        assertEquals("New Notes", updated.get().getNotes());
    }

    @Test
    void update_unknownId_returnsEmpty() throws SQLException {
        Optional<Book> updated = bookDao.update(UUID.randomUUID(), "Ghost", null, null);

        assertFalse(updated.isPresent());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesBook() throws SQLException {
        Book b = bookDao.create(project.getId(), "To Delete", null, null);

        boolean deleted = bookDao.delete(b.getId());

        assertTrue(deleted);
        assertFalse(bookDao.findById(b.getId()).isPresent());
    }

    @Test
    void delete_project_cascadesToBooks() throws SQLException {
        Book b = bookDao.create(project.getId(), "Cascaded Book", null, null);

        projectDao.delete(project.getId());

        assertFalse(bookDao.findById(b.getId()).isPresent());
    }
}
