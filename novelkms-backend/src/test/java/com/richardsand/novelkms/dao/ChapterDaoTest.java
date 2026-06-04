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
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;

class ChapterDaoTest extends NovelKmsTestBase {

    private Project project;
    private Book    book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = projectDao.create("Test Project", null);
        book = bookDao.create(project.getId(), "Test Book", null, null);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_withoutPart_linksToBook() throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, "Chapter One", "notes");

        assertNotNull(ch.getId());
        assertEquals(book.getId(), ch.getBookId());
        assertNull(ch.getPartId());
        assertEquals("Chapter One", ch.getTitle());
        assertEquals(0, ch.getDisplayOrder());
    }

    @Test
    void create_displayOrder_incrementsPerBook() throws SQLException {
        Chapter c1 = chapterDao.create(book.getId(), null, "Ch 1", null);
        Chapter c2 = chapterDao.create(book.getId(), null, "Ch 2", null);
        Chapter c3 = chapterDao.create(book.getId(), null, "Ch 3", null);

        assertEquals(0, c1.getDisplayOrder());
        assertEquals(1, c2.getDisplayOrder());
        assertEquals(2, c3.getDisplayOrder());
    }

    @Test
    void create_displayOrder_isIndependentPerBook() throws SQLException {
        Book other = bookDao.create(project.getId(), "Other Book", null, null);

        chapterDao.create(book.getId(), null, "Book1 Ch1", null);
        chapterDao.create(book.getId(), null, "Book1 Ch2", null);
        Chapter ch = chapterDao.create(other.getId(), null, "Book2 Ch1", null);

        assertEquals(0, ch.getDisplayOrder());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_returnsChapter() throws SQLException {
        Chapter created = chapterDao.create(book.getId(), null, "Persisted", null);

        Optional<Chapter> found = chapterDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals("Persisted", found.get().getTitle());
    }

    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Chapter> found = chapterDao.findById(UUID.randomUUID());

        assertFalse(found.isPresent());
    }

    // -------------------------------------------------------------------------
    // findByBookId
    // -------------------------------------------------------------------------

    @Test
    void findByBookId_empty_returnsEmptyList() throws SQLException {
        List<Chapter> chapters = chapterDao.findByBookId(book.getId());

        assertTrue(chapters.isEmpty());
    }

    @Test
    void findByBookId_returnsChaptersInOrder() throws SQLException {
        chapterDao.create(book.getId(), null, "Ch A", null);
        chapterDao.create(book.getId(), null, "Ch B", null);
        chapterDao.create(book.getId(), null, "Ch C", null);

        List<Chapter> chapters = chapterDao.findByBookId(book.getId());

        assertEquals(3, chapters.size());
        assertEquals("Ch A", chapters.get(0).getTitle());
        assertEquals("Ch B", chapters.get(1).getTitle());
        assertEquals("Ch C", chapters.get(2).getTitle());
    }

    @Test
    void findByBookId_doesNotReturnChaptersFromOtherBooks() throws SQLException {
        Book other = bookDao.create(project.getId(), "Other Book", null, null);
        chapterDao.create(book.getId(), null, "My Chapter", null);
        chapterDao.create(other.getId(), null, "Other Chapter", null);

        List<Chapter> chapters = chapterDao.findByBookId(book.getId());

        assertEquals(1, chapters.size());
        assertEquals("My Chapter", chapters.get(0).getTitle());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesTitleAndNotes() throws SQLException {
        Chapter original = chapterDao.create(book.getId(), null, "Old Title", "Old Notes");

        Optional<Chapter> updated = chapterDao.update(original.getId(), "New Title", "New Notes");

        assertTrue(updated.isPresent());
        assertEquals("New Title", updated.get().getTitle());
        assertEquals("New Notes", updated.get().getNotes());
    }

    @Test
    void update_unknownId_returnsEmpty() throws SQLException {
        Optional<Chapter> updated = chapterDao.update(UUID.randomUUID(), "Ghost", null);

        assertFalse(updated.isPresent());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesChapter() throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, "To Delete", null);

        boolean deleted = chapterDao.delete(ch.getId());

        assertTrue(deleted);
        assertFalse(chapterDao.findById(ch.getId()).isPresent());
    }

    @Test
    void delete_book_cascadesToChapters() throws SQLException {
        Chapter ch = chapterDao.create(book.getId(), null, "Cascaded", null);

        bookDao.delete(book.getId());

        assertFalse(chapterDao.findById(ch.getId()).isPresent());
    }
}
