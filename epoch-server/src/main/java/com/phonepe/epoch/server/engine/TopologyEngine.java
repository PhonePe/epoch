package com.phonepe.epoch.server.engine;

import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.SimpleTopologyEditRequest;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyStore;
import io.dropwizard.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.PathParam;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.scheduleUpdatedTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Singleton
public class TopologyEngine {

    private final TopologyStore topologyStore;
    private final Scheduler scheduler;
    private final EpochEventBus eventBus;

    public Optional<EpochTopologyDetails> createSimpleTopology(final SimpleTopologyCreateRequest request) {
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
                new EpochTaskTriggerCron(request.getCron()),
                new MailNotificationSpec(List.of(request.getNotifyEmail().split(","))));
        val topologyId = topologyId(topology);
        val existingTopology = topologyStore.get(topologyId);
        if (existingTopology.isPresent()) {
            return existingTopology; // todo, create an error here
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
        return saved;
    }

    public Optional<EpochTopologyDetails> updateTopology(@PathParam("topologyId") String topologyId,
                                                         @Valid final SimpleTopologyEditRequest request) {
        final Optional<EpochTopologyDetails> topologyDetails = topologyStore.get(topologyId);
        if (topologyDetails.isEmpty()) {
            return Optional.empty();
        }
        val topology = new EpochTopology(
                topologyId,
                new EpochContainerExecutionTask("docker-task",
                                                new DockerCoordinates(request.getDocker(), Duration.seconds(120)),
                                                List.of(new CPURequirement(request.getCpus()),
                                                        new MemoryRequirement(request.getMemory())),
                                                request.getVolumes(),
                                                LocalLoggingSpec.DEFAULT,
                                                new AnyPlacementPolicy(),
                                                Map.of(),
                                                request.getEnv()),
                new EpochTaskTriggerCron(request.getCron()),
                new MailNotificationSpec(List.of(request.getNotifyEmail().split(","))));

        val saved = topologyStore.update(topologyId, topology);
        saved.ifPresent(epochTopologyDetails -> {
            scheduleUpdatedTopology(topologyDetails.get(), epochTopologyDetails, scheduler, new Date());
            eventBus.publish(EpochStateChangeEvent.builder()
                                     .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                                     .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                                                      StateChangeEventDataTag.NEW_STATE, EpochTopologyState.ACTIVE))
                                     .build());

        });
        return saved;
    }

    public Optional<EpochTopologyDetails> get(String topologyId) {
        return topologyStore.get(topologyId);
    }
}
