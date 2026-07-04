package com.richardsand.novelkms.resource.template;

import java.sql.SQLException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.TemplateDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateResource {
    private static final Logger logger = LoggerFactory.getLogger(TemplateResource.class);
    private final TemplateDao   dao;
    @Context
    ContainerRequestContext     request;

    @Inject
    public TemplateResource(TemplateDao dao) {
        this.dao = dao;
    }

    public static class ContentRequest {
        @JsonProperty
        public String content;
    }

    @GET
    @Path("/templates/global/{type}")
    public Response getUser(@PathParam("type") String type) {
        logger.debug("TemplateResource.getUser invoked: type={}", type);
        return run(() -> Response.ok(dao.resolveForUser(CurrentUser.id(request), type(type))).build());
    }

    @PUT
    @Path("/templates/global/{type}")
    public Response putUser(@PathParam("type") String type, ContentRequest r) {
        logger.info("TemplateResource.putUser invoked: type={}", type);
        if (r == null)
            return Response.status(400).build();
        return run(() -> Response.ok(dao.upsertUser(CurrentUser.id(request), type(type), r.content == null ? "" : r.content)).build());
    }

    @POST
    @Path("/templates/global/{type}/reset")
    public Response resetUser(@PathParam("type") String type) {
        return run(() -> Response.ok(dao.resetUser(CurrentUser.id(request), type(type))).build());
    }

    @GET
    @Path("/books/{bookId}/templates/{type}")
    public Response getBook(@PathParam("bookId") UUID b, @PathParam("type") String t) {
        return run(() -> Response.ok(dao.resolveForBook(CurrentUser.id(request), b, type(t))).build());
    }

    @PUT
    @Path("/books/{bookId}/templates/{type}")
    public Response putBook(@PathParam("bookId") UUID b, @PathParam("type") String t, ContentRequest r) {
        if (r == null)
            return Response.status(400).build();
        return run(() -> Response.ok(dao.upsertBookOverride(b, type(t), r.content == null ? "" : r.content)).build());
    }

    @DELETE
    @Path("/books/{bookId}/templates/{type}")
    public Response delBook(@PathParam("bookId") UUID b, @PathParam("type") String t) {
        return run(() -> dao.deleteBookOverride(b, type(t)) ? Response.ok().build() : Response.noContent().build());
    }

    private String type(String x) {
        String u = x == null ? "" : x.trim().toUpperCase();
        if (!u.equals("COVER") && !u.equals("PART"))
            throw new BadRequestException("type must be cover or part");
        return u;
    }

    private Response run(SqlCall c) {
        try {
            return c.call();
        } catch (SQLException e) {
            logger.error("Database error in TemplateResource: {}", e.getMessage(), e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private interface SqlCall {
        Response call() throws SQLException;
    }
}
