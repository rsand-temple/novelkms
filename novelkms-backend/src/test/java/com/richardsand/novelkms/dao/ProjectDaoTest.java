package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;

class ProjectDaoTest extends NovelKmsTestBase {

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_assignsIdAndReturnsPopulatedProject() throws SQLException {
        Project p = projectDao.create("The Alone Man", "A novel");

        assertNotNull(p.getId());
        assertEquals("The Alone Man", p.getTitle());
        assertEquals("A novel", p.getDescription());
        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getUpdatedAt());
    }

    @Test
    void create_withNullDescription_succeeds() throws SQLException {
        Project p = projectDao.create("Threads of Time", null);

        assertNotNull(p.getId());
        assertEquals("Threads of Time", p.getTitle());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_returnsProject() throws SQLException {
        Project created = projectDao.create("Legacy", "Family saga");

        Optional<Project> found = projectDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals("Legacy", found.get().getTitle());
        assertEquals("Family saga", found.get().getDescription());
    }

    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Project> found = projectDao.findById(UUID.randomUUID());

        assertFalse(found.isPresent());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_empty_returnsEmptyList() throws SQLException {
        List<Project> projects = projectDao.findAll();

        assertTrue(projects.isEmpty());
    }

    @Test
    void findAll_returnsAllProjectsAlphabetically() throws SQLException {
        projectDao.create("Zebra Project", null);
        projectDao.create("Alpha Project", null);
        projectDao.create("Middle Project", null);

        List<Project> projects = projectDao.findAll();

        assertEquals(3, projects.size());
        assertEquals("Alpha Project", projects.get(0).getTitle());
        assertEquals("Middle Project", projects.get(1).getTitle());
        assertEquals("Zebra Project", projects.get(2).getTitle());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesNameAndDescription() throws SQLException {
        Project original = projectDao.create("Old Title", "Old description");

        Instant createdAt = projectDao.findById(original.getId()).get().getCreatedAt();
        
        Optional<Project> updated = projectDao.update(original.getId(), "New Title", "New description");
 
        assertTrue(updated.isPresent());
        assertEquals("New Title",        updated.get().getTitle());
        assertEquals("New description", updated.get().getDescription());
        assertFalse(updated.get().getUpdatedAt().isBefore(createdAt));    }

    @Test
    void update_unknownId_returnsEmpty() throws SQLException {
        Optional<Project> updated = projectDao.update(UUID.randomUUID(), "Ghost", null);

        assertFalse(updated.isPresent());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesProject() throws SQLException {
        Project p = projectDao.create("To Delete", null);

        boolean deleted = projectDao.delete(p.getId());

        assertTrue(deleted);
        assertFalse(projectDao.findById(p.getId()).isPresent());
    }

    @Test
    void delete_unknownId_returnsFalse() throws SQLException {
        boolean deleted = projectDao.delete(UUID.randomUUID());

        assertFalse(deleted);
    }
}
