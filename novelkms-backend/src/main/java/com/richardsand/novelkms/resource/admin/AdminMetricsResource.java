package com.richardsand.novelkms.resource.admin;

import java.sql.SQLException;

import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.dao.AdminMetricsDao;
import com.richardsand.novelkms.model.admin.AdminOverviewMetrics;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/metrics")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminMetricsResource {

    private final AdminMetricsDao adminMetricsDao;

    @Inject
    public AdminMetricsResource(AdminMetricsDao adminMetricsDao) {
        this.adminMetricsDao = adminMetricsDao;
    }

    @GET
    @Path("/overview")
    public AdminOverviewMetrics overview() throws SQLException {
        return adminMetricsDao.overview();
    }
}