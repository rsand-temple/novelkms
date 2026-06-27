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
import jakarta.ws.rs.core.Response.Status;

@ExtendWith(DropwizardExtensionsSupport.class)
class ProjectResourceTest extends NovelKmsTestBase {

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new ProjectResource(projectDao, trashService))
            .setMapper(createMapper())
            .build();

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    @Test
    void listProjects_empty_returns200AndEmptyArray() {
        Response r = RESOURCES.target("/projects").request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Project> projects = r.readEntity(new GenericType<>() {});
        assertTrue(projects.isEmpty());
    }

    @Test
    void listProjects_returnsOnlyAuthenticatedUsersProjects() throws SQLException {
        createTestProject("Alpha", null);
        createTestProject("Beta", null);
        createTestProject(OTHER_USER_ID, "Someone Else's Project", null);

        Response r = RESOURCES.target("/projects").request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        List<Project> projects = r.readEntity(new GenericType<>() {});
        assertEquals(2, projects.size());
        assertTrue(projects.stream()
                .noneMatch(p -> "Someone Else's Project".equals(p.getTitle())));
    }

    @Test
    void getProject_knownId_returns200() throws SQLException {
        Project p = createTestProject("The Alone Man", "A thriller");

        Response r = RESOURCES.target("/projects/" + p.getId()).request().get();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Project found = r.readEntity(Project.class);
        assertEquals(p.getId(), found.getId());
        assertEquals("The Alone Man", found.getTitle());
    }

    @Test
    void getProject_ownedByAnotherUser_returnsNoContent() throws SQLException {
        Project p = createTestProject(
                OTHER_USER_ID,
                "Private Project",
                null);

        Response r = RESOURCES.target("/projects/" + p.getId()).request().get();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void getProject_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/projects/" + UUID.randomUUID()).request().get();
        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void createProject_validRequest_returns201WithProject() {
        Response r = RESOURCES.target("/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of(
                        "title", "New Project",
                        "description", "A description")));

        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());
        Project created = r.readEntity(Project.class);
        assertNotNull(created.getId());
        assertEquals("New Project", created.getTitle());
        assertEquals("A description", created.getDescription());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void createProject_missingTitle_returns400() {
        Response r = RESOURCES.target("/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("description", "No title")));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void createProject_blankTitle_returns400() {
        Response r = RESOURCES.target("/projects")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("title", "   ")));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void updateProject_knownId_returns200WithUpdatedData() throws SQLException {
        Project p = createTestProject("Original Name", null);

        Response r = RESOURCES.target("/projects/" + p.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "title", "Updated Title",
                        "description", "Updated desc")));

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Project updated = r.readEntity(Project.class);
        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated desc", updated.getDescription());
    }

    @Test
    void updateProject_ownedByAnotherUser_returnsNoContent() throws SQLException {
        Project p = createTestProject(OTHER_USER_ID, "Private", null);

        Response r = RESOURCES.target("/projects/" + p.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Attempted Change")));

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void updateProject_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/projects/" + UUID.randomUUID())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("title", "Ghost")));

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }

    @Test
    void updateProject_missingTitle_returns400() throws SQLException {
        Project p = createTestProject("Project", null);

        Response r = RESOURCES.target("/projects/" + p.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of("description", "No title")));

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void deleteProject_knownId_returnsOk() throws SQLException {
        Project p = createTestProject("To Delete", null);

        Response r = RESOURCES.target("/projects/" + p.getId())
                .request()
                .delete();

        assertEquals(Status.OK.getStatusCode(), r.getStatus());
    }

    @Test
    void deleteProject_ownedByAnotherUser_returnsNoContent() throws SQLException {
        Project p = createTestProject(OTHER_USER_ID, "Do Not Delete", null);

        Response r = RESOURCES.target("/projects/" + p.getId())
                .request()
                .delete();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
        assertTrue(projectDao.findByIdForUser(
                p.getId(),
                OTHER_USER_ID).isPresent());
    }

    @Test
    void deleteProject_unknownId_returnsNoContent() {
        Response r = RESOURCES.target("/projects/" + UUID.randomUUID())
                .request()
                .delete();

        assertEquals(Status.NO_CONTENT.getStatusCode(), r.getStatus());
    }
}
