package com.phonepe.epoch.server.leadership;

import com.phonepe.epoch.server.auth.models.EpochUserRole;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 *
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class TestResource {
    @GET
    public Response get() {
        return Response.ok().build();
    }
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public Response post(String name) {
        return Response.ok().entity(Map.of("name", name)).build();
    }
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response put(String name) {
        return Response.ok().entity(Map.of("name", name)).build();
    }
    @DELETE
    public Response delete() {
        return Response.ok().build();
    }
}
