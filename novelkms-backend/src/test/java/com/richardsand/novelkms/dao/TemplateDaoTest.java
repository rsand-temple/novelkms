package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Template;
import com.richardsand.novelkms.model.book.Book;

class TemplateDaoTest extends NovelKmsTestBase {

    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = projectDao.create("Test Project", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    // -------------------------------------------------------------------------
    // Global: lazy seed
    // -------------------------------------------------------------------------

    @Test
    void getOrCreateGlobal_lazilySeedsFromDefault() throws SQLException {
        assertTrue(templateDao.findSystem(TemplateDao.TYPE_COVER).isEmpty());

        Template global = templateDao.getOrCreateSystem(TemplateDao.TYPE_COVER);

        assertNotNull(global.getId());
        assertEquals(TemplateDao.SCOPE_SYSTEM, global.getScope());
        assertEquals(TemplateDao.TYPE_COVER, global.getTemplateType());
        assertEquals(TemplateDao.DEFAULT_COVER_CONTENT, global.getContent());
        assertTrue(templateDao.findSystem(TemplateDao.TYPE_COVER).isPresent());
    }

    @Test
    void getOrCreateGlobal_isIdempotent() throws SQLException {
        Template first  = templateDao.getOrCreateSystem(TemplateDao.TYPE_PART);
        Template second = templateDao.getOrCreateSystem(TemplateDao.TYPE_PART);

        assertEquals(first.getId(), second.getId());
    }

    // -------------------------------------------------------------------------
    // Resolution + book overrides
    // -------------------------------------------------------------------------

    @Test
    void resolveForBook_fallsBackToGlobalWhenNoOverride() throws SQLException {
        Template resolved = templateDao.resolveForBook(book.getId(), TemplateDao.TYPE_COVER);

        assertEquals(TemplateDao.SCOPE_SYSTEM, resolved.getScope());
        assertEquals(TemplateDao.DEFAULT_COVER_CONTENT, resolved.getContent());
    }

    @Test
    void upsertBookOverride_thenResolvePrefersOverride() throws SQLException {
        templateDao.upsertBookOverride(book.getId(), TemplateDao.TYPE_COVER, "<p>Book cover</p>");

        Template resolved = templateDao.resolveForBook(book.getId(), TemplateDao.TYPE_COVER);

        assertEquals(TemplateDao.SCOPE_BOOK, resolved.getScope());
        assertEquals(book.getId(), resolved.getBookId());
        assertEquals("<p>Book cover</p>", resolved.getContent());
    }

    @Test
    void upsertBookOverride_secondCallUpdatesSameRow() throws SQLException {
        Template first  = templateDao.upsertBookOverride(book.getId(), TemplateDao.TYPE_PART, "<p>v1</p>");
        Template second = templateDao.upsertBookOverride(book.getId(), TemplateDao.TYPE_PART, "<p>v2</p>");

        assertEquals(first.getId(), second.getId());
        assertEquals("<p>v2</p>", second.getContent());
    }

    @Test
    void overrideDoesNotAffectGlobal() throws SQLException {
        templateDao.upsertBookOverride(book.getId(), TemplateDao.TYPE_COVER, "<p>Book cover</p>");

        Template global = templateDao.getOrCreateSystem(TemplateDao.TYPE_COVER);

        assertNotEquals("<p>Book cover</p>", global.getContent());
        assertEquals(TemplateDao.DEFAULT_COVER_CONTENT, global.getContent());
    }

    @Test
    void deleteBookOverride_revertsToGlobal() throws SQLException {
        templateDao.upsertBookOverride(book.getId(), TemplateDao.TYPE_COVER, "<p>Book cover</p>");

        boolean deleted = templateDao.deleteBookOverride(book.getId(), TemplateDao.TYPE_COVER);

        assertTrue(deleted);
        Template resolved = templateDao.resolveForBook(book.getId(), TemplateDao.TYPE_COVER);
        assertEquals(TemplateDao.SCOPE_SYSTEM, resolved.getScope());
    }

    @Test
    void deleteBookOverride_whenNoneExists_returnsFalse() throws SQLException {
        assertFalse(templateDao.deleteBookOverride(book.getId(), TemplateDao.TYPE_PART));
    }
}
