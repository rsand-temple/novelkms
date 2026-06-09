package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import lombok.ToString;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {
    private static Logger    logger = LoggerFactory.getLogger(ProjectResource.class);
    private final ProjectDao projectDao;

    @Inject
    public ProjectResource(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    @ToString
    public static class CreateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String description;
    }

    @ToString
    public static class UpdateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String description;
        @JsonProperty
        public String authorFirstName;
        @JsonProperty
        public String authorLastName;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    @Path("/projects")
    public Response listProjects() {
        try {
            List<Project> projects = projectDao.findAll();
            return Response.ok(projects).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @GET
    @Path("/projects/{id}")
    public Response getProject(@PathParam("id") UUID id) {
        try {
            return projectDao.findById(id)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @POST
    @Path("/projects")
    public Response createProject(CreateRequest req) {
        if (StringUtils.isBlank(req.title)) {
            logger.debug("title is required");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Project project = projectDao.create(req.title, req.description);
            return Response.status(Response.Status.CREATED).entity(project).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @PUT
    @Path("/projects/{id}")
    public Response updateProject(@PathParam("id") UUID id, UpdateRequest req) {
        if (StringUtils.isBlank(req.title)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            return projectDao.update(id, req.title, req.description,
                    req.authorFirstName, req.authorLastName)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    @DELETE
    @Path("/projects/{id}")
    public Response deleteProject(@PathParam("id") UUID id) {
        try {
            return projectDao.delete(id)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException sqle) {
            return serverError(sqle);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}