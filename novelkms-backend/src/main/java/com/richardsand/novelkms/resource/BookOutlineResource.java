package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.book.BookOutlineDao;
import com.richardsand.novelkms.model.book.OutlineRef;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The book outline: parts and direct-book chapters in one linear sequence.
 *
 * <p>This replaces the former {@code PUT /books/{id}/parts/reorder} and
 * {@code PUT /books/{id}/chapters/reorder} endpoints. Those renumbered one type
 * in isolation, which is incoherent now that parts and direct-book chapters
 * share a single {@code display_order} sequence — renumbering parts 0..n-1
 * would collide head-on with the chapters interleaved among them.
 *
 * <p>The path deliberately ends in {@code /reorder}: {@code TenantAuthorizationFilter}
 * buffers and ownership-checks every {@code id}-named JSON property on bodies
 * whose path ends in {@code /reorder} or {@code /move}, so each item in the
 * payload is authorized without any new filter code. The {@code books/{bookId}}
 * path segment is checked by the same filter's path-ID pass.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookOutlineResource {

    private static final Logger  logger = LoggerFactory.getLogger(BookOutlineResource.class);

    private final BookOutlineDao outlineDao;

    @Inject
    public BookOutlineResource(BookOutlineDao outlineDao) {
        this.outlineDao = outlineDao;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

    /**
     * Body: {@code { "items": [ {"type":"CHAPTER","id":"…"}, {"type":"PART","id":"…"} ] }}
     * in the desired outline order.
     */
    public static class ReorderOutlineRequest {
        @JsonProperty
        public List<OutlineRef> items;
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/books/{bookId}/outline
     *
     * The book's parts and direct-book chapters as one ordered list of typed
     * references. The frontend normally merges its existing parts and chapters
     * queries client-side by display order rather than calling this, but the
     * endpoint gives the ordering an authoritative server-side reading for
     * tests, debugging, and any consumer that does not already hold both lists.
     */
    @GET
    @Path("/books/{bookId}/outline")
    public Response getOutline(@PathParam("bookId") UUID bookId) {
        logger.debug("BookOutlineResource.getOutline invoked: bookId={}", bookId);
        try {
            return Response.ok(outlineDao.findByBookId(bookId)).build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    /**
     * PUT /api/books/{bookId}/outline/reorder
     *
     * Reorders the book's whole outline. The body must carry the complete
     * ordered list of the book's parts and direct-book chapters; items omitted
     * from the list keep their current position, which would leave the sequence
     * inconsistent, so callers always send the full list.
     */
    @PUT
    @Path("/books/{bookId}/outline/reorder")
    public Response reorderOutline(@PathParam("bookId") UUID bookId, ReorderOutlineRequest req) {
        logger.info("BookOutlineResource.reorderOutline invoked: bookId={}, count={}",
                bookId, req == null || req.items == null ? 0 : req.items.size());

        if (req == null || req.items == null || req.items.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("items is required").build();
        }
        for (OutlineRef item : req.items) {
            if (item == null || item.id() == null || item.type() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("each item requires a type and an id").build();
            }
        }

        try {
            outlineDao.reorder(bookId, req.items);
            return Response.noContent().build();
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    // -------------------------------------------------------------------------

    private Response serverError(SQLException sqle) {
        logger.error("Database error in BookOutlineResource: {}", sqle.getMessage(), sqle);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
