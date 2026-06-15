package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(BookResource.class);
    private final BookDao       bookDao;

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
        public String shortTitle;
        @JsonProperty
        public String notes;
    }

    public static class UpdateRequest {
        @JsonProperty
        public String  title;
        @JsonProperty
        public String  subtitle;
        @JsonProperty
        public String  shortTitle;
        @JsonProperty
        public String  notes;
        @JsonProperty
        public Boolean pageLayoutEnabled;
        @JsonProperty
        public String  pageSizePreset;
        @JsonProperty
        public Double  pageWidthIn;
        @JsonProperty
        public Double  pageHeightIn;
        @JsonProperty
        public Double  pageMarginTopIn;
        @JsonProperty
        public Double  pageMarginBottomIn;
        @JsonProperty
        public Double  pageMarginInnerIn;
        @JsonProperty
        public Double  pageMarginOuterIn;
    }

    public static class CoverImageRequest {
        @JsonProperty
        public String imageData;
        @JsonProperty
        public String mimeType;
    }

    // -------------------------------------------------------------------------
    // Book CRUD
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
            Book book = bookDao.create(projectId, req.title, req.subtitle, req.shortTitle, req.notes);
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
            return bookDao.update(
                    id,
                    req.title, req.subtitle, req.shortTitle, req.notes,
                    req.pageLayoutEnabled != null && req.pageLayoutEnabled,
                    req.pageSizePreset,
                    req.pageWidthIn, req.pageHeightIn,
                    req.pageMarginTopIn, req.pageMarginBottomIn,
                    req.pageMarginInnerIn, req.pageMarginOuterIn)
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
    // Word count
    // -------------------------------------------------------------------------

    /**
     * Returns the total word count for a book including scene content,
     * chapter headings, and part headings. Used by the editor status bar
     * when the book is selected and by the WORDS template token.
     *
     * Returns { "wordCount": N }
     */
    @GET
    @Path("/books/{id}/word-count")
    public Response getWordCount(@PathParam("id") UUID id) {
        try {
            int count = bookDao.getTotalWordCount(id);
            return Response.ok(Map.of("wordCount", count)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Cover image
    // -------------------------------------------------------------------------

    @GET
    @Path("/books/{id}/cover-image")
    @Produces(MediaType.WILDCARD)
    public Response getCoverImage(@PathParam("id") UUID id) {
        try {
            return bookDao.getCoverImage(id)
                    .map(img -> Response.ok(img.data(), img.mimeType()).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{id}/cover-image")
    public Response setCoverImage(@PathParam("id") UUID id, CoverImageRequest req) {
        if (req == null || req.imageData == null || req.mimeType == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("imageData and mimeType are required").build();
        }
        if (!req.mimeType.startsWith("image/")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("mimeType must be an image type (e.g. image/jpeg)").build();
        }
        try {
            byte[]  bytes = Base64.getDecoder().decode(req.imageData);
            boolean found = bookDao.setCoverImage(id, bytes, req.mimeType);
            return found
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("imageData must be valid base64").build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{id}/cover-image")
    public Response deleteCoverImage(@PathParam("id") UUID id) {
        try {
            return bookDao.deleteCoverImage(id)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.info("SQLException: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}