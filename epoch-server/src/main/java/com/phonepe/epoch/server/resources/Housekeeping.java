package com.phonepe.epoch.server.resources;

import com.google.inject.Inject;
import com.phonepe.epoch.server.managed.LeadershipManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

@Path("/housekeeping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Inject))
public class Housekeeping {

    private final LeadershipManager manager;

    @GET
    @Path("/v1/leader")
    public Response leader() {
        val leader = manager.leader();
        return leader
                .map(s -> Response.ok(Map.of("leader", s)).build())
                .orElseGet(() -> Response.status(
                        Response.Status.NOT_FOUND).entity(Map.of("leader", "no leader available")).build());
    }
}
