package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.support.DatabaseIntegrationTest;

class TenantIsolationDaoTest extends DatabaseIntegrationTest {

    @Test
    void projectQueriesAreScopedToOwner() throws Exception {
        AppUser alice = createUser("alice", "alice@example.com", "Alice");
        AppUser bob = createUser("bob", "bob@example.com", "Bob");
        ProjectDao dao = new ProjectDao(dataSource);

        Project project = dao.createForUser(alice.id(), "Alice Project", null);

        assertEquals(1, dao.findAllForUser(alice.id()).size());
        assertTrue(dao.findAllForUser(bob.id()).isEmpty());
        assertTrue(dao.findByIdForUser(project.getId(), bob.id()).isEmpty());
        assertFalse(dao.deleteForUser(bob.id(), project.getId()));
        assertTrue(dao.findByIdForUser(project.getId(), alice.id()).isPresent());
    }

    @Test
    void everyHierarchyLevelInheritsProjectOwnership() throws Exception {
        AppUser alice = createUser("alice-tree", "alice-tree@example.com", "Alice");
        AppUser bob = createUser("bob-tree", "bob-tree@example.com", "Bob");
        Hierarchy tree = createHierarchy(alice.id(), "A");
        TenantAccessDao access = new TenantAccessDao(dataSource);

        assertAll(
                () -> assertTrue(access.ownsProject(alice.id(), tree.projectId())),
                () -> assertTrue(access.ownsBook(alice.id(), tree.bookId())),
                () -> assertTrue(access.ownsPart(alice.id(), tree.partId())),
                () -> assertTrue(access.ownsChapter(alice.id(), tree.chapterId())),
                () -> assertTrue(access.ownsScene(alice.id(), tree.sceneId())),
                () -> assertFalse(access.ownsProject(bob.id(), tree.projectId())),
                () -> assertFalse(access.ownsBook(bob.id(), tree.bookId())),
                () -> assertFalse(access.ownsPart(bob.id(), tree.partId())),
                () -> assertFalse(access.ownsChapter(bob.id(), tree.chapterId())),
                () -> assertFalse(access.ownsScene(bob.id(), tree.sceneId()))
        );
    }

    @Test
    void foreignWordCountIsNotReturned() throws Exception {
        AppUser alice = createUser("alice-count", "alice-count@example.com", "Alice");
        AppUser bob = createUser("bob-count", "bob-count@example.com", "Bob");
        Hierarchy tree = createHierarchy(alice.id(), "Count");
        ProjectDao dao = new ProjectDao(dataSource);

        assertTrue(dao.getTotalWordCountForUser(alice.id(), tree.projectId()) >= 1);
        assertEquals(-1, dao.getTotalWordCountForUser(bob.id(), tree.projectId()));
    }
}
