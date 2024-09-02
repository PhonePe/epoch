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
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.models.topology.SimpleTopologyEditRequest;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.error.EpochError;
import com.phonepe.epoch.server.error.EpochErrorCode;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.execution.QuartzCronUtility;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyStore;
import io.dropwizard.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.PathParam;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.scheduleUpdatedTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.removeScheduledTopology;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Singleton
public class TopologyEngine {

    private final TopologyStore topologyStore;
    private final Scheduler scheduler;
    private final EpochEventBus eventBus;

    public Optional<EpochTopologyDetails> createSimpleTopology(final SimpleTopologyCreateRequest request) {
        validateCronExpression(request.getCron());
        val topology = new EpochTopology(
                request.getName(),
                new EpochContainerExecutionTask("docker-task",
                                                new DockerCoordinates(request.getDocker(), Duration.seconds(120)),
                                                List.of(new CPURequirement(request.getCpus()),
                                                        new MemoryRequirement(request.getMemory())),
                                                request.getVolumes(),
                                                List.of(),
                                                LocalLoggingSpec.DEFAULT,
                                                new AnyPlacementPolicy(),
                                                Map.of(),
                                                request.getEnv(),
                                                null),
                new EpochTaskTriggerCron(request.getCron()),
                new MailNotificationSpec(List.of(request.getNotifyEmail().split(","))));
        val topologyId = topologyId(topology);
        val existingTopology = topologyStore.get(topologyId);
        if (existingTopology.isPresent()) {
            throw EpochError.raise(EpochErrorCode.TOPOLOGY_ALREADY_EXISTS, Map.of("name", existingTopology.get().getTopology().getName()));
        }
        val saved = topologyStore.save(topology);
        saved.ifPresent(epochTopologyDetails -> {
            scheduleTopology(epochTopologyDetails, scheduler);
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
        validateCronExpression(request.getCron());
        final Optional<EpochTopologyDetails> topologyDetails = topologyStore.get(topologyId);
        if (topologyDetails.isEmpty()) {
            throw EpochError.raise(EpochErrorCode.TOPOLOGY_NOT_FOUND, Map.of("id", topologyId));
        }
        val topology = new EpochTopology(
                topologyId,
                new EpochContainerExecutionTask("docker-task",
                                                new DockerCoordinates(request.getDocker(), Duration.seconds(120)),
                                                List.of(new CPURequirement(request.getCpus()),
                                                        new MemoryRequirement(request.getMemory())),
                                                request.getVolumes(),
                                                List.of(),
                                                LocalLoggingSpec.DEFAULT,
                                                new AnyPlacementPolicy(),
                                                Map.of(),
                                                request.getEnv(),
                                                null),
                new EpochTaskTriggerCron(request.getCron()),
                new MailNotificationSpec(List.of(request.getNotifyEmail().split(","))));

        val updated = topologyStore.update(topologyId, topology);
        updated.ifPresent(updatedTopologyDetails -> {
            scheduleUpdatedTopology(topologyDetails.get(), updatedTopologyDetails, scheduler);
            eventBus.publish(EpochStateChangeEvent.builder()
                                     .type(EpochEventType.TOPOLOGY_UPDATED)
                                     .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                                                      StateChangeEventDataTag.NEW_STATE, updatedTopologyDetails.getState(),
                                                      StateChangeEventDataTag.NEW_TRIGGER, updatedTopologyDetails.getTopology().getTrigger()))
                                     .build());

        });
        return updated;
    }

    public Optional<EpochTopologyDetails> save(final EpochTopology topology) {
        val stored = topologyStore.save(topology);
        stored.ifPresent(epochTopologyDetails -> {
            scheduleTopology(epochTopologyDetails, scheduler);
            eventBus.publish(EpochStateChangeEvent.builder()
                    .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                    .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, epochTopologyDetails.getId(),
                            StateChangeEventDataTag.NEW_STATE, epochTopologyDetails.getState()))
                    .build());
        });
        return stored;
    }

    public Optional<EpochTopologyDetails> get(String topologyId) {
        return topologyStore.get(topologyId);
    }

    public List<EpochTopologyDetails> list(Predicate<EpochTopologyDetails> filter) {
        return topologyStore.list(filter);
    }

    public boolean delete(String topologyId) {
        val deleted = topologyStore.delete(topologyId);
        if (deleted) {
            scheduler.delete(topologyId);
            eventBus.publish(EpochStateChangeEvent.builder()
                    .type(EpochEventType.TOPOLOGY_STATE_CHANGED)
                    .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                            StateChangeEventDataTag.NEW_STATE, EpochTopologyState.DELETED))
                    .build());
        }
        return deleted;
    }

    public Optional<String> scheduleNow(String topologyId) {
        return get(topologyId)
                .flatMap(topology -> scheduler.scheduleNow(topologyId));
    }

    public Optional<EpochTopologyDetails> update(String topologyId, final EpochTopology topology) {
        val topologyDetails = get(topologyId);
        if (topologyDetails.isEmpty()) {
            throw EpochError.raise(EpochErrorCode.TOPOLOGY_NOT_FOUND, Map.of("id", topologyId));
        }
        val previousTopology = topologyDetails.get();
        val updated = topologyStore.update(topologyId, topology);
        updated.ifPresent(updatedTopologyDetails -> {
            scheduleUpdatedTopology(previousTopology, updatedTopologyDetails, scheduler);
            eventBus.publish(EpochStateChangeEvent.builder()
                    .type(EpochEventType.TOPOLOGY_UPDATED)
                    .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                            StateChangeEventDataTag.NEW_STATE, updatedTopologyDetails.getState(),
                            StateChangeEventDataTag.NEW_TRIGGER, updatedTopologyDetails.getTopology().getTrigger()))
                    .build());
        });
        return updated;
    }

    public Optional<EpochTopologyDetails> updateState(String topologyId, EpochTopologyState state) {
        val topologyDetails = get(topologyId);
        if (topologyDetails.isEmpty()) {
            throw EpochError.raise(EpochErrorCode.TOPOLOGY_NOT_FOUND, Map.of("id", topologyId));
        }
        val previousTopology = topologyDetails.get();
        val updated = topologyStore.updateState(topologyId, state);
        updated.ifPresent(updatedTopologyDetails -> {
            if (updatedTopologyDetails.getState() == EpochTopologyState.ACTIVE) {
                scheduleTopology(updatedTopologyDetails, scheduler);
            } else {
                removeScheduledTopology(previousTopology, scheduler);
            }
            eventBus.publish(EpochStateChangeEvent.builder()
                    .type(EpochEventType.TOPOLOGY_UPDATED)
                    .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, topologyId,
                            StateChangeEventDataTag.NEW_STATE, updatedTopologyDetails.getState(),
                            StateChangeEventDataTag.NEW_TRIGGER, updatedTopologyDetails.getTopology().getTrigger()))
                    .build());
        });
        return updated;
    }

    private static void validateCronExpression(final String cronExpression) {
        if (!QuartzCronUtility.isValidCronExpression(cronExpression)) {
            throw EpochError.raise(EpochErrorCode.INPUT_VALIDATION_ERROR, Map.of(
                    "field", "cron", "cron", cronExpression,
                    "message", "check https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html for help"));
        }
    }
}
