package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.dao.user.UserStyleDao;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.model.Style;
import com.richardsand.novelkms.model.StyleDefinition;
import com.richardsand.novelkms.support.DatabaseIntegrationTest;

class UserStyleScopeDaoTest extends DatabaseIntegrationTest {

    @Test
    void styleResolutionIsBookProjectUserSystem() throws Exception {
        AppUser alice = createUser("style-alice", "style-alice@example.com", "Alice");
        Hierarchy tree = createHierarchy(alice.id(), "Style");
        UserStyleDao dao = new UserStyleDao(dataSource);
        String key = "normal";

        assertEquals("SYSTEM", dao.resolveUser(alice.id(), key).getScope());

        dao.upsertUser(alice.id(), key, definition("User Font"));
        assertStyle("USER", "User Font", dao.resolveBook(alice.id(), tree.bookId(), key));

        dao.upsertProject(tree.projectId(), key, definition("Project Font"));
        assertStyle("PROJECT", "Project Font", dao.resolveBook(alice.id(), tree.bookId(), key));

        dao.upsertBook(tree.bookId(), key, definition("Book Font"));
        assertStyle("BOOK", "Book Font", dao.resolveBook(alice.id(), tree.bookId(), key));

        assertTrue(dao.deleteBook(tree.bookId(), key));
        assertStyle("PROJECT", "Project Font", dao.resolveBook(alice.id(), tree.bookId(), key));

        assertTrue(dao.deleteProject(tree.projectId(), key));
        assertStyle("USER", "User Font", dao.resolveBook(alice.id(), tree.bookId(), key));

        assertTrue(dao.deleteUser(alice.id(), key));
        assertEquals("SYSTEM", dao.resolveBook(alice.id(), tree.bookId(), key).getScope());
    }

    @Test
    void styleDefaultsDoNotCollideBetweenUsers() throws Exception {
        AppUser alice = createUser("style-a", "style-a@example.com", "Alice");
        AppUser bob = createUser("style-b", "style-b@example.com", "Bob");
        UserStyleDao dao = new UserStyleDao(dataSource);

        dao.upsertUser(alice.id(), "normal", definition("Alice Font"));
        dao.upsertUser(bob.id(), "normal", definition("Bob Font"));

        assertEquals("Alice Font", dao.resolveUser(alice.id(), "normal").getDefinition().getFontFamily());
        assertEquals("Bob Font", dao.resolveUser(bob.id(), "normal").getDefinition().getFontFamily());
    }

    private static StyleDefinition definition(String fontFamily) {
        return StyleDefinition.builder()
                .fontFamily(fontFamily)
                .fontSize("1rem")
                .firstLineIndent("0")
                .textIndent("0")
                .spacingBefore("0")
                .spacingAfter("0")
                .build();
    }

    private static void assertStyle(String scope, String font, Style style) {
        assertEquals(scope, style.getScope());
        assertEquals(font, style.getDefinition().getFontFamily());
    }
}
