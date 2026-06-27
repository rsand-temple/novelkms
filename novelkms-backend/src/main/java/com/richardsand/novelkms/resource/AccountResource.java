package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AccountDao;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
public class AccountResource {
    static Logger logger = LoggerFactory.getLogger(AccountResource.class);

    public record UpdateAccountRequest(
            String firstname,
            String lastname,
            String displayname,
            String mobile) {
    }

    private AccountDao accountDao = null;

    @Inject
    public AccountResource(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @GET
    @Path("/account")
    public Response getCurrentAccount(@Context ContainerRequestContext request) {
        UUID id = CurrentUser.id(request);
        return getAccount(id);
    }
    
    private Response getAccount(@PathParam("id") UUID id) {
        logger.debug("getAccount invoked: id={}", id);
        try {
            return accountDao.getAccount(id)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }
    
    @PUT
    @Path("/account")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateAccount(UpdateAccountRequest uar, @Context ContainerRequestContext request) {
        // TODO id must be from logged in user unless admin role;
        UUID id = CurrentUser.id(request);
        logger.debug("updateAccount invoked: id={}", id);
        try {
            return accountDao.updateAccount(id, uar.firstname, uar.lastname, uar.displayname, uar.mobile)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    private Response serverError(SQLException sqle) {
        logger.error("Database error in AccountResource: {}", sqle.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(sqle.getMessage()).build();
    }
}
