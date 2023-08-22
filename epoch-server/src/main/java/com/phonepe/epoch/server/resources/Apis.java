package com.phonepe.epoch.server.resources;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.server.auth.models.EpochUserRole;
import com.phonepe.epoch.server.engine.TopologyEngine;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.phonepe.epoch.server.utils.EpochUtils.appName;
import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

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
    private final TopologyEngine topologyEngine;

    private final Scheduler scheduler;
    private final DroveClientManager clientManager;
    private final TaskExecutionEngine taskExecutionEngine;

    @Inject
    public Apis(
            TopologyStore topologyStore,
            TopologyRunInfoStore runInfoStore,
            final TopologyEngine topologyEngine,
            Scheduler scheduler,
            DroveClientManager clientManager,
            TaskExecutionEngine taskExecutionEngine) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.topologyEngine = topologyEngine;
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

    @PUT
    @Path("/topologies")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<EpochTopologyDetails> update(@NotNull @Valid final EpochTopology topology) {
        val topologyId = topologyId(topology);
        val topologyDetails = topologyStore.get(topologyId);
        if (topologyDetails.isEmpty()) {
            return ApiResponse.failure("Topology " + topology.getName() + " doesn't exist with ID: " + topologyId);
        }
        val saved = topologyStore.update(topologyId, topology);
        saved.ifPresent(epochTopologyDetails -> {
            runInfoStore.deleteAll(topologyId);
            scheduleTopology(epochTopologyDetails, scheduler, new Date());
        });
        return saved
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("Could not update topology"));
    }

    @PUT
    @Path("/topologies/{topologyId}")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public ApiResponse<EpochTopologyDetails> edit(@NotNull @Valid final SimpleTopologyEditRequest request,
                                                  @NotEmpty @PathParam("topologyId") final String topologyId) {
        return topologyEngine.updateTopology(topologyId, request)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("Could not update topology"));
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
                .flatMap(topology -> scheduler.scheduleNow(topologyId))
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
        val matchStates = runStates.isEmpty()
                          ? EnumSet.allOf(EpochTopologyRunState.class)
                          : runStates;
        return ApiResponse.success(runInfoStore.list(topologyId, r -> matchStates.contains(r.getState())));
    }

    @GET
    @Path("/topologies/{topologyId}/runs/{runId}")
    public ApiResponse<EpochTopologyRunInfo> getRun(
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
            return ApiResponse.failure(new CancelResponse(false, "No task found for the provided id"),
                                       "No task exists for " + topologyId + "/" + runId + "/" + taskId);
        }
        val response = taskExecutionEngine.cancelTask(task.getTaskId());
        return response.isSuccess()
               ? ApiResponse.success(response)
               : ApiResponse.failure(
                       response,
                       "Could not cancel task " + topologyId + "/" + runId + "/" + taskId + ". Error: " + response.getMessage());
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
                    return clientManager.getClient()
                            .leader()
                            .map(leader -> URI.create(leader + "/tasks/" + appName() + "/" + task.getUpstreamId()));
                })
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("No log exists for " + topologyId + "/" + runId + "/" + taskId));
    }

    private static String noTopoError(String topologyId) {
        return "No such topology: " + topologyId;
    }

}
