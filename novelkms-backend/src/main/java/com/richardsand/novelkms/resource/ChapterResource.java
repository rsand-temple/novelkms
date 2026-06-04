package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.model.Chapter;

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
public class ChapterResource {

    private final ChapterDao chapterDao;

    @Inject
    public ChapterResource(ChapterDao chapterDao) {
        this.chapterDao = chapterDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    public static class CreateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String notes;
        /** Optional — omit to place chapter directly under the book. */
        @JsonProperty
        public UUID   partId;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String title;
        @JsonProperty
        public String notes;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{bookId}/chapters")
    public Response listChapters(@PathParam("bookId") UUID bookId) {
        try {
            List<Chapter> chapters = chapterDao.findByBookId(bookId);
            return Response.ok(chapters).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/chapters/{id}")
    public Response getChapter(@PathParam("id") UUID id) {
        try {
            return chapterDao.findById(id)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/books/{bookId}/chapters")
    public Response createChapter(@PathParam("bookId") UUID bookId, CreateRequest req) {
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            Chapter chapter = chapterDao.create(bookId, req.partId, req.title, req.notes);
            return Response.status(Response.Status.CREATED).entity(chapter).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/chapters/{id}")
    public Response updateChapter(@PathParam("id") UUID id, UpdateRequest req) {
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            return chapterDao.update(id, req.title, req.notes)
                    .map(ch -> Response.ok(ch).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/chapters/{id}")
    public Response deleteChapter(@PathParam("id") UUID id) {
        try {
            return chapterDao.delete(id)
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
