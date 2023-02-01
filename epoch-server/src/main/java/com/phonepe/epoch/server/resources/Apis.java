package com.phonepe.epoch.server.resources;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.server.auth.models.EpochUserRole;
import com.phonepe.epoch.server.managed.DroveClientManager;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.remote.CancelResponse;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.*;

import static com.phonepe.epoch.server.utils.EpochUtils.*;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@PermitAll
public class Apis {
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;

    private final Scheduler scheduler;
    private final DroveClientManager clientManager;
    private final TaskExecutionEngine taskExecutionEngine;

    @Inject
    public Apis(
            TopologyStore topologyStore, TopologyRunInfoStore runInfoStore, Scheduler scheduler,
            DroveClientManager clientManager,
            TaskExecutionEngine taskExecutionEngine) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.scheduler = scheduler;
        this.clientManager = clientManager;
        this.taskExecutionEngine = taskExecutionEngine;
    }

    @POST
    @Path("/topologies")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<EpochTopologyDetails> save(@NotNull @Valid final EpochTopology topology) {
        val topologyId = topologyId(topology);
        if (topologyStore.get(topologyId).isPresent()) {
            return ApiResponse.failure("Topology " + topology.getName() + " already exists with ID: " + topologyId);
        }
        val saved = topologyStore.save(topology);
        saved.ifPresent(epochTopologyDetails -> scheduleTopology(epochTopologyDetails, scheduler, new Date()));
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
                .orElseGet(() -> ApiResponse.failure(noTopoError(topologyId)));
    }

    @PUT
    @Path("/topologies/{topologyId}/run")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<Map<String, String>> runTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.get(topologyId)
                .map(topology -> scheduler.scheduleNow(topologyId).orElse(null))
                .filter(Objects::nonNull)
                .map(runId -> ApiResponse.success(Map.of("runId", runId)))
                .orElseGet(() -> ApiResponse.failure(noTopoError(topologyId)));
    }

    @PUT
    @Path("/topologies/{topologyId}/pause")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<EpochTopologyDetails> pauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.updateState(topologyId, EpochTopologyState.PAUSED)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure(noTopoError(topologyId)));
    }

    @PUT
    @Path("/topologies/{topologyId}/unpause")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<EpochTopologyDetails> unpauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyStore.updateState(topologyId, EpochTopologyState.ACTIVE)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure(noTopoError(topologyId)));
    }

    @DELETE
    @Path("/topologies/{topologyId}")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
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

    @POST
    @Path("/topologies/{topologyId}/runs/{runId}/tasks/{taskId}/kill")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<CancelResponse> killTask(
            @NotEmpty @PathParam("topologyId") final String topologyId,
            @NotEmpty @PathParam("runId") final String runId,
            @NotEmpty @PathParam("taskId") final String taskId) {
        val task = runInfoStore.get(topologyId, runId)
                .map(runInfo -> runInfo.getTasks().get(taskId))
                .orElse(null);
        if (null == task) {
            return ApiResponse.failure("No task exists for " + topologyId + "/" + runId + "/" + taskId);
        }
        val response = taskExecutionEngine.cancelTask(task.getTaskId());
        return response.success()
               ? ApiResponse.success(response)
               : ApiResponse.failure(
                       response,
                       "Could not cancel task " + topologyId + "/" + runId + "/" + taskId + ". Error: " + response.message());
    }

    @GET
    @Path("/topologies/{topologyId}/runs/{runId}/tasks/{taskId}/log")
    public ApiResponse<URI> logLink(
            @NotEmpty @PathParam("topologyId") final String topologyId,
            @NotEmpty @PathParam("runId") final String runId,
            @NotEmpty @PathParam("taskId") final String taskId) {
        return runInfoStore.get(topologyId, runId)
                .flatMap(runInfo -> {
                    val task = runInfo.getTasks().get(taskId);
                    if (task == null) {
                        return Optional.empty();
                    }
                    return clientManager.getClient().leader()
                            .map(leader -> URI.create(leader + "/tasks/" + appName() + "/" + task.getUpstreamId()));
                })
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("Not log exists for " + topologyId + "/" + runId + "/" + taskId));
    }

    private static String noTopoError(String topologyId) {
        return "No such topology: " + topologyId;
    }

}
