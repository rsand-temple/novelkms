package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.dao.AdminUserDao;
import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.model.admin.AdminUserSummary;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminUserResource {

    private final AdminUserDao adminUserDao;

    @Inject
    public AdminUserResource(AdminUserDao adminUserDao) {
        this.adminUserDao = adminUserDao;
    }

    @GET
    public List<AdminUserSummary> search(
            @QueryParam("query") String query,
            @QueryParam("limit") Integer limit) throws SQLException {

        return adminUserDao.search(query, limit);
    }

    @GET
    @Path("/{userId}")
    public AdminUserDetail byId(@PathParam("userId") UUID userId) throws SQLException {
        return adminUserDao.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}