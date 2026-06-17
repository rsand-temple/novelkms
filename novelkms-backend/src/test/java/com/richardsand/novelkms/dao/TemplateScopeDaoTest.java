package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.model.Template;
import com.richardsand.novelkms.support.DatabaseIntegrationTest;

class TemplateScopeDaoTest extends DatabaseIntegrationTest {

    @Test
    void templateResolutionIsBookThenUserThenSystem() throws Exception {
        AppUser alice = createUser("template-alice", "template-alice@example.com", "Alice");
        Hierarchy tree = createHierarchy(alice.id(), "Template");
        TemplateDao dao = new TemplateDao(dataSource);

        Template system = dao.resolveForUser(alice.id(), TemplateDao.TYPE_COVER);
        assertEquals("SYSTEM", system.getScope());

        dao.upsertUser(alice.id(), TemplateDao.TYPE_COVER, "<p>Alice default</p>");
        Template user = dao.resolveForBook(alice.id(), tree.bookId(), TemplateDao.TYPE_COVER);
        assertEquals("USER", user.getScope());
        assertEquals("<p>Alice default</p>", user.getContent());

        dao.upsertBookOverride(tree.bookId(), TemplateDao.TYPE_COVER, "<p>Book override</p>");
        Template book = dao.resolveForBook(alice.id(), tree.bookId(), TemplateDao.TYPE_COVER);
        assertEquals("BOOK", book.getScope());
        assertEquals("<p>Book override</p>", book.getContent());

        assertTrue(dao.deleteBookOverride(tree.bookId(), TemplateDao.TYPE_COVER));
        assertEquals("USER", dao.resolveForBook(alice.id(), tree.bookId(), TemplateDao.TYPE_COVER).getScope());

        Template reset = dao.resetUser(alice.id(), TemplateDao.TYPE_COVER);
        assertEquals("SYSTEM", reset.getScope());
    }

    @Test
    void userDefaultsDoNotCollide() throws Exception {
        AppUser alice = createUser("template-a", "template-a@example.com", "Alice");
        AppUser bob = createUser("template-b", "template-b@example.com", "Bob");
        TemplateDao dao = new TemplateDao(dataSource);

        dao.upsertUser(alice.id(), TemplateDao.TYPE_PART, "<p>Alice</p>");
        dao.upsertUser(bob.id(), TemplateDao.TYPE_PART, "<p>Bob</p>");

        assertEquals("<p>Alice</p>", dao.resolveForUser(alice.id(), TemplateDao.TYPE_PART).getContent());
        assertEquals("<p>Bob</p>", dao.resolveForUser(bob.id(), TemplateDao.TYPE_PART).getContent());
    }
}
