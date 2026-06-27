package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.dao.AccountDao;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

    @Context
    ContainerRequestContext request;

    private AccountDao accountDao = null;

    @Inject
    public AccountResource(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @GET
    @Path("/account")
    public Response getAccount() {
        UUID id = CurrentUser.id(request);
        logger.debug("getAccount invoked: id={}", id);
        try {
            return accountDao.getAccount(id)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }
    
    @POST
    @Path("/account")
    public Response updateAccount(@FormParam("firstname") String firstname, 
            @FormParam("lastname") String lastname, 
            @FormParam("displayname") String displayname, 
            @FormParam("mobile") String mobile,
            @FormParam("email") String email) {
        UUID id = CurrentUser.id(request);
        logger.debug("updateAccount invoked: id={}, email={}", id, email);
        try {
            return accountDao.updateAccount(id, firstname, lastname, displayname, mobile, email)
                    .map(b -> Response.ok(b).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (SQLException e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/account/{email}")
    public Response deleteAccount(@PathParam("email") String email) {
        UUID id = CurrentUser.id(request);
        logger.debug("deleteAccount invoked: id={}, email={}", id, email);
        try {
            return accountDao.delete(id, email) ? Response.ok().build() : Response.status(Response.Status.NOT_FOUND).build();
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
