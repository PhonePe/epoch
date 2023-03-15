package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.notification.NotificationSpecVisitor;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
public class EventMailDataConverter {
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;

    private final List<String> defaultReceivers;

    @Inject
    public EventMailDataConverter(TopologyStore topologyStore, TopologyRunInfoStore runInfoStore,
                                  List<String> defaultReceivers) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.defaultReceivers = defaultReceivers;
    }

    public Optional<MailData> convert(final EpochStateChangeEvent stateChangeEvent) {
        if (!stateChangeEvent.getType().equals(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)) {
            log.debug("Ignoring event of type {}", stateChangeEvent.getType());
            return Optional.empty();
        }
        val newState = (EpochTopologyRunState) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.NEW_STATE);
        val topologyId = (String) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_ID);
        val runId = (String) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_RUN_ID);
        val runInfo = runInfoStore.get(topologyId, runId).orElse(null);
        val emailIds = topologyStore.get(topologyId)
                .map(topologyDetails -> topologyDetails.getTopology().getNotify())
                .map(notificationSpec -> notificationSpec.accept(new NotificationSpecVisitor<List<String>>() {
                    @Override
                    public List<String> visit(MailNotificationSpec mailSpec) {
                        return mailSpec.getEmails();
                    }

                    @Override
                    public List<String> visit(BlackholeNotificationSpec blackhole) {
                        return List.of();
                    }
                }))
                .filter(emails -> !emails.isEmpty())
                .orElseGet(() -> defaultReceivers);
        if (emailIds.isEmpty()) {
            log.warn("No mail notification spec provided. Ignoring state change message");
            return Optional.empty();
        }

        return buildMailData(emailIds, newState, topologyId, runId, runInfo);
    }

    private static Optional<MailData> buildMailData(
            List<String> emailIds,
            EpochTopologyRunState newState,
            String topologyId,
            String runId,
            EpochTopologyRunInfo runInfo) {
        return switch (newState) {
            case RUNNING, SKIPPED -> Optional.empty();
            case COMPLETED, SUCCESSFUL -> Optional.of(
                    new MailData(
                            emailIds,
                            String.format("Topology run %s/%s completed successfully", topologyId, runId),
                            String.format("Tasks completed: %s in %d ms",
                                          runInfo.getTasks()
                                                  .values()
                                                  .stream()
                                                  .map(EpochTopologyRunTaskInfo::getTaskId)
                                                  .toList(),
                                          runInfo.getUpdated().getTime() - runInfo.getCreated().getTime())));
            case FAILED -> {
                val failedTask = runInfo.getTasks().values().stream()
                        .filter(taskRun -> taskRun.getState().equals(EpochTaskRunState.FAILED))
                        .findFirst()
                        .orElse(null);
                val subject = String.format("Topology run %s/%s failed", topologyId, runId);
                if (failedTask != null) {
                    yield Optional.of(
                            new MailData(
                                    emailIds,
                                    subject,
                                    String.format("Task %s with upstream ID %s failed with error: %s",
                                                  failedTask.getTaskId(),
                                                  failedTask.getUpstreamId(),
                                                  failedTask.getErrorMessage())));
                }
                yield Optional.of(new MailData(emailIds, subject, "All tasks seem to have succeeded."));
            }
        };
    }
}
