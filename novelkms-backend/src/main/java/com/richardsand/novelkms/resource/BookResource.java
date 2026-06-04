package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.model.Book;

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

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {

    private final BookDao bookDao;

    @Inject
    public BookResource(BookDao bookDao) {
        this.bookDao = bookDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String subtitle;
        @JsonProperty
        public String notes;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String subtitle;
        @JsonProperty
        public String notes;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    @Path("/projects/{projectId}/books")
    public Response listBooks(@PathParam("projectId") UUID projectId) {
        try {
            List<Book> books = bookDao.findByProjectId(projectId);
            return Response.ok(books).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/books/{id}")
    public Response getBook(@PathParam("id") UUID id) {
        try {
            return bookDao.findById(id)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/projects/{projectId}/books")
    public Response createBook(@PathParam("projectId") UUID projectId, CreateRequest req) {
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Book book = bookDao.create(projectId, req.title, req.subtitle, req.notes);
            return Response.status(Response.Status.CREATED).entity(book).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{id}")
    public Response updateBook(@PathParam("id") UUID id, UpdateRequest req) {
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            return bookDao.update(id, req.title, req.subtitle, req.notes)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{id}")
    public Response deleteBook(@PathParam("id") UUID id) {
        try {
            return bookDao.delete(id)
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
