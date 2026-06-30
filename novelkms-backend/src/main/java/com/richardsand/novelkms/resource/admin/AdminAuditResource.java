package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.dao.AdminAuditDao;
import com.richardsand.novelkms.model.AdminAuditLogEntry;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/audit")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminAuditResource {

    private final AdminAuditDao adminAuditDao;

    @Inject
    public AdminAuditResource(AdminAuditDao adminAuditDao) {
        this.adminAuditDao = adminAuditDao;
    }

    @GET
    @Path("/recent")
    public List<AdminAuditLogEntry> recent(@QueryParam("limit") Integer limit) throws SQLException {
        return adminAuditDao.recent(limit);
    }

    @GET
    @Path("/{id}")
    public AdminAuditLogEntry byId(@PathParam("id") UUID id) throws SQLException {
        return adminAuditDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Admin audit entry not found"));
    }

    @GET
    @Path("/users/{userId}")
    public List<AdminAuditLogEntry> forTargetUser(
            @PathParam("userId") UUID userId,
            @QueryParam("limit") Integer limit) throws SQLException {

        return adminAuditDao.forTargetUser(userId, limit);
    }

    @GET
    @Path("/admins/{adminUserId}")
    public List<AdminAuditLogEntry> byAdminUser(
            @PathParam("adminUserId") UUID adminUserId,
            @QueryParam("limit") Integer limit) throws SQLException {

        return adminAuditDao.byAdminUser(adminUserId, limit);
    }
}