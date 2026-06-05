package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Project;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
class ProjectResourceTest extends NovelKmsTestBase {

    // Static: ResourceExtension initializes before @BeforeAll.
    // NovelKmsTestBase's static block guarantees projectDao exists first.
    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new ProjectResource(projectDao))
            .setMapper(createMapper())
            .build();

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    // -------------------------------------------------------------------------
    // GET /api/projects
    // -------------------------------------------------------------------------

    @Test
    void listProjects_empty_returns200AndEmptyArray() {
        Response r = RESOURCES.target("/api/projects").request().get();

        assertEquals(200, r.getStatus());
        List<Project> projects = r.readEntity(new GenericType<>() {});
        assertTrue(projects.isEmpty());
    }

    @Test
    void listProjects_returnsAllProjects() throws SQLException {
        projectDao.create("Alpha", null);
        projectDao.create("Beta", null);

        Response r = RESOURCES.target("/api/projects").request().get();

        assertEquals(200, r.getStatus());
        List<Project> projects = r.readEntity(new GenericType<>() {});
        assertEquals(2, projects.size());
    }

    // -------------------------------------------------------------------------
    // GET /api/projects/{id}
    // -------------------------------------------------------------------------

    @Test
    void getProject_knownId_returns200() throws SQLException {
        Project p = projectDao.create("The Alone Man", "A thriller");

        Response r = RESOURCES.target("/api/projects/" + p.getId()).request().get();

        assertEquals(200, r.getStatus());
        Project found = r.readEntity(Project.class);
        assertEquals(p.getId(), found.getId());
        assertEquals("The Alone Man", found.getTitle());
    }

    @Test
    void getProject_unknownId_returns404() {
        Response r = RESOURCES.target("/api/projects/" + UUID.randomUUID()).request().get();

        assertEquals(404, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // POST /api/projects
    // -------------------------------------------------------------------------

    @Test
    void createProject_validRequest_returns201WithProject() {
        Response r = RESOURCES.target("/api/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("title", "New Project", "description", "A description")));

        assertEquals(201, r.getStatus());
        Project created = r.readEntity(Project.class);
        assertNotNull(created.getId());
        assertEquals("New Project", created.getTitle());
        assertEquals("A description", created.getDescription());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void createProject_missingName_returns400() {
        Response r = RESOURCES.target("/api/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("description", "No name")));

        assertEquals(400, r.getStatus());
    }

    @Test
    void createProject_blankName_returns400() {
        Response r = RESOURCES.target("/api/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("name", "   ")));

        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // PUT /api/projects/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateProject_knownId_returns200WithUpdatedData() throws SQLException {
        Project p = projectDao.create("Original Name", null);

        Response r = RESOURCES.target("/api/projects/" + p.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Updated Title", "description", "Updated desc")));

        assertEquals(200, r.getStatus());
        Project updated = r.readEntity(Project.class);
        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated desc", updated.getDescription());
    }

    @Test
    void updateProject_unknownId_returns404() {
        Response r = RESOURCES.target("/api/projects/" + UUID.randomUUID())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Ghost")));

        assertEquals(404, r.getStatus());
    }

    @Test
    void updateProject_missingName_returns400() throws SQLException {
        Project p = projectDao.create("Project", null);

        Response r = RESOURCES.target("/api/projects/" + p.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("description", "No name")));

        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/projects/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteProject_knownId_returns204() throws SQLException {
        Project p = projectDao.create("To Delete", null);

        Response r = RESOURCES.target("/api/projects/" + p.getId())
                .request()
                .delete();

        assertEquals(204, r.getStatus());
    }

    @Test
    void deleteProject_unknownId_returns404() {
        Response r = RESOURCES.target("/api/projects/" + UUID.randomUUID())
                .request()
                .delete();

        assertEquals(404, r.getStatus());
    }
}
