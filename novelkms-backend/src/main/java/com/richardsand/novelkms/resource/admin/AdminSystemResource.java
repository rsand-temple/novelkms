package com.richardsand.novelkms.resource.admin;

import java.util.Map;

import com.richardsand.novelkms.auth.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/system")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
public class AdminSystemResource {
    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return Map.of(
                "ok", true,
                "admin", true);
    }
}