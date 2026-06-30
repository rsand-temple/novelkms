package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.model.UserSubscription;
import com.richardsand.novelkms.model.admin.AdminBillingDetail;
import com.richardsand.novelkms.model.admin.GrantFamilyAccessRequest;
import com.richardsand.novelkms.service.admin.AdminBillingService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/billing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminBillingResource {

    private final AdminBillingService adminBillingService;

    @Inject
    public AdminBillingResource(AdminBillingService adminBillingService) {
        this.adminBillingService = adminBillingService;
    }

    @GET
    @Path("/users/{userId}")
    public AdminBillingDetail billingDetail(@PathParam("userId") UUID userId) throws SQLException {
        return adminBillingService.billingDetail(userId);
    }

    @POST
    @Path("/users/{userId}/family-access")
    public UserSubscription grantFamilyAccess(
            @Context ContainerRequestContext request,
            @PathParam("userId") UUID userId,
            GrantFamilyAccessRequest body) throws SQLException {

        GrantFamilyAccessRequest effectiveBody = body == null
                ? new GrantFamilyAccessRequest(null, null)
                : body;

        UUID adminUserId = CurrentUser.id(request);

        return adminBillingService.grantFamilyAccess(
                adminUserId,
                userId,
                effectiveBody.reason(),
                effectiveBody.note());
    }
}