package com.phonepe.epoch.server.resources;

import com.google.inject.Inject;
import com.phonepe.epoch.server.managed.LeadershipManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.PermitAll;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

@Path("/housekeeping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@PermitAll
@AllArgsConstructor(onConstructor = @__(@Inject))
public class Housekeeping {

    private final LeadershipManager manager;

    @GET
    @Path("/v1/leader")
    public Response leader() {
        final Optional<String> leader = manager.leader();
        if (leader.isPresent()) {
            return Response.ok(Map.of("leader", leader.get())).build();
        }
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("leader", "no leader available")).build();
    }
}
