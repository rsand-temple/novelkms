package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.model.Project;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    private final ProjectDao projectDao;

    @Inject
    public ProjectResource(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateRequest {
        @JsonProperty
        public String name;
        @JsonProperty
        public String description;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String name;
        @JsonProperty
        public String description;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    public Response listProjects() {
        try {
            List<Project> projects = projectDao.findAll();
            return Response.ok(projects).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/{id}")
    public Response getProject(@PathParam("id") UUID id) {
        try {
            return projectDao.findById(id)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    public Response createProject(CreateRequest req) {
        if (req == null || req.name == null || req.name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("name is required").build();
        }
        try {
            Project project = projectDao.create(req.name, req.description);
            return Response.status(Response.Status.CREATED).entity(project).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateProject(@PathParam("id") UUID id, UpdateRequest req) {
        if (req == null || req.name == null || req.name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("name is required").build();
        }
        try {
            return projectDao.update(id, req.name, req.description)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteProject(@PathParam("id") UUID id) {
        try {
            return projectDao.delete(id)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(e.getMessage()).build();
    }
}
