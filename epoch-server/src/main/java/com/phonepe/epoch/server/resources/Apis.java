package com.phonepe.epoch.server.resources;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class Apis {
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;

    private final Scheduler scheduler;

    @Inject
    public Apis(TopologyStore topologyStore, TopologyRunInfoStore runInfoStore, Scheduler scheduler) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.scheduler = scheduler;
    }

    @POST
    @Path("/topologies")
    public ApiResponse<EpochTopologyDetails> save(@NotNull @Valid final EpochTopology topology) {
        val topologyId = topologyId(topology);
        if (topologyStore.get(topologyId).isPresent()) {
            return ApiResponse.failure("Topology " + topology.getName() + " already exists with ID: " + topologyId);
        }
        val saved = topologyStore.save(topology);
        if (saved.isPresent()) {
            scheduleTopology(saved.get(), scheduler, new Date());
        }
        return saved
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("Could not create topology"));
    }

    @GET
    @Path("/topologies")
    public ApiResponse<List<EpochTopologyDetails>> listTopologies() {
        return ApiResponse.success(topologyStore.list(t -> true));
    }

    @GET
    @Path("/topologies/{topologyId}")
    public ApiResponse<EpochTopologyDetails> getTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.get(topologyId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("No such topology: " + topologyId));
    }

    @PUT
    @Path("/topologies/{topologyId}/pause")
    public ApiResponse<EpochTopologyDetails> pauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.updateState(topologyId, EpochTopologyState.PAUSED)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("No such topology: " + topologyId));
    }

    @PUT
    @Path("/topologies/{topologyId}/unpause")
    public ApiResponse<EpochTopologyDetails> unpauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.updateState(topologyId, EpochTopologyState.ACTIVE)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("No such topology: " + topologyId));
    }

    @DELETE
    @Path("/topologies/{topologyId}")
    public ApiResponse<Boolean> deleteTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return ApiResponse.success(topologyStore.delete(topologyId) && runInfoStore.deleteAll(topologyId));
    }

    @GET
    @Path("/topologies/{topologyId}/runs")
    public ApiResponse<Collection<EpochTopologyRunInfo>> listRuns(
            @NotEmpty @PathParam("topologyId") final String topologyId,
            @QueryParam("state") final Set<EpochTaskRunState> runStates) {
        val matchStates = null == runStates || runStates.isEmpty()
                          ? EnumSet.allOf(EpochTopologyRunState.class)
                          : runStates;
        return ApiResponse.success(runInfoStore.list(topologyId, r -> matchStates.contains(r.getState())));
    }

    @GET
    @Path("/topologies/{topologyId}/runs/{runId}")
    public ApiResponse<EpochTopologyRunInfo> listRuns(
            @NotEmpty @PathParam("topologyId") final String topologyId,
            @NotEmpty @PathParam("runId") final String runId) {
        return runInfoStore.get(topologyId, runId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("Not run exists for " + topologyId + "/" + runId));
    }
}
