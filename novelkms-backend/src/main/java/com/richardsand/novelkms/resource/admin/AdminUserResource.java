package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.dao.admin.AdminUserDao;
import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.model.admin.AdminUserSummary;
import com.richardsand.novelkms.model.admin.HardDeleteUserRequest;
import com.richardsand.novelkms.service.admin.AdminUserDeleteService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminUserResource {

    private final AdminUserDao           adminUserDao;
    private final AdminUserDeleteService adminUserDeleteService;

    @Inject
    public AdminUserResource(AdminUserDao adminUserDao,
            AdminUserDeleteService adminUserDeleteService) {
        this.adminUserDao           = adminUserDao;
        this.adminUserDeleteService = adminUserDeleteService;
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

    /**
     * Permanently deletes a user account and all associated data, and cancels any
     * active Stripe subscription. This action is irreversible.
     *
     * <p>Guards enforced by the service:
     * <ul>
     *   <li>Admin cannot delete their own account.</li>
     *   <li>Target must not hold the {@code ADMIN} role.</li>
     * </ul>
     *
     * <p>Returns {@code 204 No Content} on success.
     */
    @POST
    @Path("/{userId}/hard-delete")
    public Response hardDelete(
            @Context ContainerRequestContext request,
            @PathParam("userId") UUID userId,
            HardDeleteUserRequest body) throws Exception {

        UUID   adminUserId = CurrentUser.id(request);
        String reason      = body != null ? body.reason() : null;

        adminUserDeleteService.hardDelete(adminUserId, userId, reason);

        return Response.noContent().build();
    }
}
