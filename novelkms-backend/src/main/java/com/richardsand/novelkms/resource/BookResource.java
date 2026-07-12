package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.model.book.Book;
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

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {
    private static final Logger logger = LoggerFactory.getLogger(BookResource.class);
    private final BookDao       bookDao;
    private final TrashService  trashService;

    /** One year in seconds — used for immutable image cache responses. */
    private static final int CACHE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    @Inject
    public BookResource(BookDao bookDao, TrashService trashService) {
        this.bookDao = bookDao;
        this.trashService = trashService;
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

        // Page layout moved to the scoped page_layout bundle (see PageLayoutResource).
        // These fields are accepted but ignored so the current frontend's book-save
        // payload still deserializes; they are removed once the Page Layout tab lands.
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
        logger.debug("BookResource.listBooks invoked: projectId={}", projectId);
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
        logger.debug("BookResource.getBook invoked: id={}", id);
        try {
            return bookDao.findById(id)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NO_CONTENT).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/projects/{projectId}/books")
    public Response createBook(@PathParam("projectId") UUID projectId, CreateRequest req) {
        logger.info("BookResource.createBook invoked: projectId={}", projectId);
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
        logger.info("BookResource.updateBook invoked: id={}", id);
        if (req == null || req.title == null || req.title.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("title is required").build();
        }
        try {
            return bookDao.update(id, req.title, req.subtitle, req.shortTitle, req.notes)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NO_CONTENT).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{id}")
    public Response deleteBook(@PathParam("id") UUID id, @Context ContainerRequestContext request) {
        logger.info("BookResource.deleteBook invoked: id={}", id);
        try {
            return trashService.trashBook(CurrentUser.id(request), id).isPresent()
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Word count
    // -------------------------------------------------------------------------

    /**
     * Returns the total word and paragraph count for a book including scene
     * content, chapter headings, and part headings. Used by the editor status
     * bar when the book is selected (word count for the WORDS template token;
     * both counts feed the estimated page count) and by the WORDS template
     * token.
     *
     * Returns { "wordCount": N, "paragraphCount": M }
     */
    @GET
    @Path("/books/{id}/word-count")
    public Response getWordCount(@PathParam("id") UUID id) {
        logger.debug("BookResource.getWordCount invoked: id={}", id);
        try {
            int wordCount = bookDao.getTotalWordCount(id);
            int paragraphCount = bookDao.getTotalParagraphCount(id);
            return Response.ok(Map.of("wordCount", wordCount, "paragraphCount", paragraphCount)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Cover image
    // -------------------------------------------------------------------------

    /**
     * Serves the raw cover image bytes with the stored MIME type.
     *
     * The frontend appends {@code ?t={book.updatedAt}} to the URL, so each
     * image revision produces a distinct URL. This lets us set an aggressive
     * {@code Cache-Control: max-age=31536000, immutable} header — the browser
     * caches the response indefinitely for a given query-string, and a new
     * {@code updatedAt} value after upload/delete naturally bypasses the cache.
     */
    @GET
    @Path("/books/{id}/cover-image")
    @Produces(MediaType.WILDCARD)
    public Response getCoverImage(@PathParam("id") UUID id) {
        logger.debug("BookResource.getCoverImage invoked: id={}", id);
        try {
            return bookDao.getCoverImage(id)
                    .map(img -> Response.ok(img.data(), img.mimeType())
                            .header("Cache-Control",
                                    "public, max-age=" + CACHE_MAX_AGE_SECONDS
                                            + ", no-transform, immutable")
                            .build())
                    .orElse(Response.status(Response.Status.NO_CONTENT).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/books/{id}/cover-image")
    public Response setCoverImage(@PathParam("id") UUID id, CoverImageRequest req) {
        logger.info("BookResource.setCoverImage invoked: id={}, mimeType={}", id, req == null ? null : req.mimeType);
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
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid cover image upload for bookId={}: {}", id, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("imageData must be valid base64").build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/books/{id}/cover-image")
    public Response deleteCoverImage(@PathParam("id") UUID id) {
        logger.info("BookResource.deleteCoverImage invoked: id={}", id);
        try {
            return bookDao.deleteCoverImage(id)
                    ? Response.ok().build()
                    : Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.error("Database error in BookResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}