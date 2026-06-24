package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richardsand.novelkms.dao.PageLayoutDao;
import com.richardsand.novelkms.model.PageLayout;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Scoped page layout (export/preview only).
 *
 * <ul>
 *   <li>{@code /projects/{id}/page-layout} — PROJECT override (PROJECT -> SYSTEM)</li>
 *   <li>{@code /books/{id}/page-layout} — BOOK override (BOOK -> PROJECT -> SYSTEM)</li>
 * </ul>
 * Both tenant-authorized by the {@code projects/{id}} / {@code books/{id}}
 * segment. GET returns the resolved layout including {@code scope}, so the
 * dialog's per-tab override toggle can tell "own override" from "inherited".
 * PUT is lenient: missing margins fall back to the factory defaults in the DAO.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PageLayoutResource {

    private final PageLayoutDao dao;

    @Inject
    public PageLayoutResource(PageLayoutDao dao) {
        this.dao = dao;
    }

    /** Request body for PUT — the page-layout value fields. */
    public static class PageLayoutRequest {
        @JsonProperty public boolean pageLayoutEnabled;
        @JsonProperty public String  pageSizePreset;
        @JsonProperty public Double  pageWidthIn;
        @JsonProperty public Double  pageHeightIn;
        @JsonProperty public Double  pageMarginTopIn;
        @JsonProperty public Double  pageMarginBottomIn;
        @JsonProperty public Double  pageMarginInnerIn;
        @JsonProperty public Double  pageMarginOuterIn;

        PageLayout toValue() {
            return PageLayout.builder()
                    .pageLayoutEnabled(pageLayoutEnabled)
                    .pageSizePreset(pageSizePreset)
                    .pageWidthIn(pageWidthIn)
                    .pageHeightIn(pageHeightIn)
                    .pageMarginTopIn(pageMarginTopIn)
                    .pageMarginBottomIn(pageMarginBottomIn)
                    .pageMarginInnerIn(pageMarginInnerIn)
                    .pageMarginOuterIn(pageMarginOuterIn)
                    .build();
        }
    }

    // ── Project override ──────────────────────────────────────────────────────

    @GET
    @Path("/projects/{id}/page-layout")
    public Response getProject(@PathParam("id") UUID projectId) {
        return run(() -> Response.ok(dao.resolveProject(projectId)).build());
    }

    @PUT
    @Path("/projects/{id}/page-layout")
    public Response putProject(@PathParam("id") UUID projectId, PageLayoutRequest body) {
        return run(() -> Response.ok(dao.upsertProject(projectId, body.toValue())).build());
    }

    @DELETE
    @Path("/projects/{id}/page-layout")
    public Response deleteProject(@PathParam("id") UUID projectId) {
        return run(() -> dao.deleteProject(projectId)
                ? Response.noContent().build()
                : Response.status(404).build());
    }

    // ── Book override ─────────────────────────────────────────────────────────

    @GET
    @Path("/books/{id}/page-layout")
    public Response getBook(@PathParam("id") UUID bookId) {
        return run(() -> Response.ok(dao.resolveBook(bookId)).build());
    }

    @PUT
    @Path("/books/{id}/page-layout")
    public Response putBook(@PathParam("id") UUID bookId, PageLayoutRequest body) {
        return run(() -> Response.ok(dao.upsertBook(bookId, body.toValue())).build());
    }

    @DELETE
    @Path("/books/{id}/page-layout")
    public Response deleteBook(@PathParam("id") UUID bookId) {
        return run(() -> dao.deleteBook(bookId)
                ? Response.noContent().build()
                : Response.status(404).build());
    }

    private Response run(Call c) {
        try {
            return c.go();
        } catch (SQLException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private interface Call {
        Response go() throws SQLException;
    }
}
