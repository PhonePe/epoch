package com.phonepe.epoch.server.resources;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.server.store.TopologyStore;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class Apis {
    private final TopologyStore topologyStore;

    @Inject
    public Apis(TopologyStore topologyStore) {
        this.topologyStore = topologyStore;
    }

    @POST
    @Path("/topologies")
    public ApiResponse<EpochTopologyDetails> save(@NotEmpty @Valid final EpochTopology topology) {
        return topologyStore.save(topology)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("Could not create topology"));
    }

    @GET
    @Path("/topologies")
    public ApiResponse<List<EpochTopologyDetails>> listTopologis() {

        return ApiResponse.success(topologyStore.list(t -> true));
    }

    @GET
    @Path("/topologies/{topologyId}")
    public ApiResponse<EpochTopologyDetails> getTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.get(topologyId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("No such topology: " + topologyId));
    }

    @DELETE
    @Path("/topologies/{topologyId}")
    public ApiResponse<Boolean> deleteTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return ApiResponse.success(topologyStore.delete(topologyId));
    }


}
