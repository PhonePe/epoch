package com.phonepe.epoch.server.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.ui.views.HomeView;
import com.phonepe.epoch.server.ui.views.TopologyDetailsView;
import io.dropwizard.util.Duration;
import io.dropwizard.util.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
public class UI {

    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore topologyRunInfoStore;
    private final Scheduler scheduler;
    private final EpochEventBus eventBus;

    private final ObjectMapper mapper;

    @Inject
    public UI(TopologyStore topologyStore, TopologyRunInfoStore topologyRunInfoStore,
              Scheduler scheduler,
              EpochEventBus eventBus, ObjectMapper mapper) {
        this.topologyStore = topologyStore;
        this.topologyRunInfoStore = topologyRunInfoStore;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.mapper = mapper;
    }

    @GET
    public HomeView home() {
        return new HomeView();
    }

    @GET
    @Path("/topologies/{topologyId}")
    public TopologyDetailsView topologyDetails(@PathParam("topologyId") final String topologyId) {
        val details = topologyStore.get(topologyId)
                .map(topologyDetails -> {
                    try {
                        return new TopologyDetailsView(topologyDetails,
                                                       mapper.writerWithDefaultPrettyPrinter()
                                                               .writeValueAsString(topologyDetails));
                    }
                    catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(null);
        if (null == details) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return details;
    }

    @POST
    @Path("/topologies/{topologyId}/run")
    public Response runTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        val runId = topologyStore.get(topologyId)
                .map(topology -> scheduler.scheduleNow(topologyId))
                .orElse(null);
        if(Strings.isNullOrEmpty(runId)) {
            log.error("Could not start a run for topology {}", topologyId);
        }
        else {
            log.info("An instant run was started for topology {}", topologyId);

        }
        return redirectToHome();
    }

    @POST
    @Path("/topologies/{topologyId}/pause")
    public Response pauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return setTopoState(topologyId, EpochTopologyState.PAUSED);
    }

    @POST
    @Path("/topologies/{topologyId}/unpause")
    public Response unpauseTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        return setTopoState(topologyId, EpochTopologyState.ACTIVE);
    }

    @POST
    @Path("/topologies/{topologyId}/delete")
    public Response deleteTopology(@NotEmpty @PathParam("topologyId") final String topologyId) {
        if (topologyStore.delete(topologyId)) {
            topologyRunInfoStore.deleteAll(topologyId);
            eventBus.publish(EpochStateChangeEvent.builder()
                                     .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                                     .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                                                      StateChangeEventDataTag.NEW_STATE, EpochTopologyState.DELETED))
                                     .build());
            log.info("Topology {} has been deleted", topologyId);
        }
        else {
            log.warn("Delete called on invalid topology {}", topologyId);
        }
        return redirectToHome();
    }

    private static Response redirectToHome() {
        return Response.seeOther(URI.create("/")).build();
    }

    @POST
    @Path("/topologies/create")
    public Response createSimpleTopology(@Valid final SimpleTopologyCreateRequest request) {
        val topology = new EpochTopology(
                request.getName(),
                new EpochContainerExecutionTask("docker-task",
                                                new DockerCoordinates(request.getDocker(), Duration.seconds(120)),
                                                List.of(new CPURequirement(request.getCpus()),
                                                        new MemoryRequirement(request.getMemory())),
                                                request.getVolumes(),
                                                LocalLoggingSpec.DEFAULT,
                                                new AnyPlacementPolicy(),
                                                Map.of(),
                                                request.getEnv()),
                new EpochTaskTriggerCron(request.getCron()));
        val topologyId = topologyId(topology);
        if (topologyStore.get(topologyId).isPresent()) {
            return redirectToHome();
        }
        val saved = topologyStore.save(topology);
        saved.ifPresent(epochTopologyDetails -> {
            scheduleTopology(epochTopologyDetails, scheduler, new Date());
            eventBus.publish(EpochStateChangeEvent.builder()
                                     .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                                     .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                                                      StateChangeEventDataTag.NEW_STATE, EpochTopologyState.ACTIVE))
                                     .build());

        });
        return redirectToHome();
    }

    private Response setTopoState(String topologyId, EpochTopologyState topoState) {
        val topology = topologyStore.updateState(topologyId, topoState).orElse(null);
        if (null == topology) {
            log.warn("Pause called for invalid topology id: {}", topologyId);
        }
        else {
            eventBus.publish(EpochStateChangeEvent.builder()
                                     .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                                     .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                                                      StateChangeEventDataTag.NEW_STATE, topoState))
                                     .build());
            log.info("Pause completed. Topology {} has been changed to stage: {}",
                     topologyId, topology.getState());

        }
        return redirectToHome();
    }
}
