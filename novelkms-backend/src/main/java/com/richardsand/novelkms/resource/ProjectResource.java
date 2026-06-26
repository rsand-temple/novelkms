package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.service.TrashService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.ToString;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {
    private static final Logger logger = LoggerFactory.getLogger(ProjectResource.class);
    private final ProjectDao projectDao;
    private final TrashService trashService;

    @Inject
    public ProjectResource(ProjectDao projectDao, TrashService trashService) {
        this.projectDao = projectDao;
        this.trashService = trashService;
    }

    @ToString
    public static class CreateRequest {
        @JsonProperty public String title;
        @JsonProperty public String description;
    }

    @ToString
    public static class UpdateRequest {
        @JsonProperty public String title;
        @JsonProperty public String description;
        @JsonProperty public String authorFirstName;
        @JsonProperty public String authorLastName;
        @JsonProperty public String copyright;
        @JsonProperty public String displayName;
        @JsonProperty public String emailAddress;
        @JsonProperty public String phoneNumber;
    }

    @GET
    @Path("/projects")
    public Response listProjects(@Context ContainerRequestContext request) {
        logger.debug("ProjectResource.listProjects invoked");
        try {
            List<Project> projects = projectDao.findAllForUser(CurrentUser.id(request));
            return Response.ok(projects).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/projects/{id}")
    public Response getProject(@PathParam("id") UUID id, @Context ContainerRequestContext request) {
        logger.debug("ProjectResource.getProject invoked: id={}", id);
        try {
            return projectDao.findByIdForUser(id, CurrentUser.id(request))
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/projects")
    public Response createProject(CreateRequest body, @Context ContainerRequestContext request) {
        logger.info("ProjectResource.createProject invoked");
        if (body == null || StringUtils.isBlank(body.title)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("title is required").build();
        }
        try {
            Project project = projectDao.createForUser(CurrentUser.id(request), body.title, body.description);
            return Response.status(Response.Status.CREATED).entity(project).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/projects/{id}")
    public Response updateProject(@PathParam("id") UUID id, UpdateRequest body,
                                  @Context ContainerRequestContext request) {
        if (body == null || StringUtils.isBlank(body.title)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("title is required").build();
        }
        Project project = Project.builder()
                .id(id)
                .title(body.title)
                .description(body.description)
                .authorFirstName(body.authorFirstName)
                .authorLastName(body.authorLastName)
                .copyright(body.copyright)
                .displayName(body.displayName)
                .emailAddress(body.emailAddress)
                .phoneNumber(body.phoneNumber)
                .build();
        try {
            return projectDao.updateForUser(CurrentUser.id(request), project)
                    .map(p -> Response.ok(p).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/projects/{id}/word-count")
    public Response getProjectWordCount(@PathParam("id") UUID id,
                                        @Context ContainerRequestContext request) {
        try {
            int count = projectDao.getTotalWordCountForUser(CurrentUser.id(request), id);
            return count < 0
                    ? Response.status(Response.Status.NOT_FOUND).build()
                    : Response.ok(Map.of("wordCount", count)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/projects/{id}")
    public Response deleteProject(@PathParam("id") UUID id,
                                  @Context ContainerRequestContext request) {
        try {
            return trashService.trashProject(CurrentUser.id(request), id).isPresent()
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    private Response serverError(SQLException e) {
        logger.error("Database error in ProjectResource: {}", e.getMessage(), e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
}
