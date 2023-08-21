package com.phonepe.epoch.server.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyEditRequest;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.auth.models.EpochUser;
import com.phonepe.epoch.server.auth.models.EpochUserRole;
import com.phonepe.epoch.server.engine.TopologyEngine;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.ui.views.HomeView;
import com.phonepe.epoch.server.ui.views.TopologyDetailsView;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import io.dropwizard.auth.Auth;
import io.dropwizard.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@PermitAll
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class UI {

    private final TopologyEngine topologyEngine;
    private final ObjectMapper mapper;

    @GET
    @IgnoreInJacocoGeneratedReport(reason = "Template context cannot be injected for UI tests")
    public HomeView home(@Auth final EpochUser user) {
        return new HomeView(user.getRole());
    }

    @GET
    @Path("/topologies/{topologyId}")
    @IgnoreInJacocoGeneratedReport(reason = "Template context cannot be injected for UI tests")
    public TopologyDetailsView topologyDetails(
            @PathParam("topologyId") final String topologyId,
            @Auth final EpochUser user) {
        val details = topologyEngine.get(topologyId)
                .map(topologyDetails -> createTopologyDetailsView(topologyId, user, topologyDetails))
                .orElse(null);
        if (null == details) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return details;
    }

    @IgnoreInJacocoGeneratedReport(reason = "Parent function is ignored")
    private TopologyDetailsView createTopologyDetailsView(
            String topologyId,
            EpochUser user,
            EpochTopologyDetails topologyDetails) {
        try {
            return new TopologyDetailsView(user.getRole(), topologyId, topologyDetails,
                                           mapper.writerWithDefaultPrettyPrinter()
                                                   .writeValueAsString(topologyDetails));
        }
        catch (JsonProcessingException e) {
            log.error("Error creating topology details view for topology " + topologyId + ": " + e.getMessage(), e);
            return null;
        }
    }

    @POST
    @Path("/topologies/create")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public Response createSimpleTopology(@Valid final SimpleTopologyCreateRequest request) {
        topologyEngine.createSimpleTopology(request);
        return redirectToHome();
    }

    @POST
    @Path("/topologies/{topologyId}/update")
    @RolesAllowed(EpochUserRole.Values.EPOCH_READ_WRITE_ROLE)
    public Response updateTopology(@PathParam("topologyId")String topologyId, @Valid final EpochTopologyEditRequest request) {
        topologyEngine.updateTopology(topologyId, request);
        return redirectToHome();
    }

    private static Response redirectToHome() {
        return Response.seeOther(URI.create("/")).build();
    }

}
