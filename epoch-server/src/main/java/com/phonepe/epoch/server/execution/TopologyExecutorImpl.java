package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTaskVisitor;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.remote.TaskExecutionContext;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.statemanagement.TaskStateElaborator;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;

/**
 *
 */
@Slf4j
@Singleton
public final class TopologyExecutorImpl implements TopologyExecutor {

    private final TaskExecutionEngine taskEngine;
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;

    private final EpochEventBus eventBus;

    @Inject
    public TopologyExecutorImpl(
            TaskExecutionEngine taskEngine,
            TopologyStore topologyStore,
            TopologyRunInfoStore runInfoStore,
            EpochEventBus eventBus) {
        this.taskEngine = taskEngine;
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.eventBus = eventBus;
    }

    @Override
    public Optional<EpochTopologyRunInfo> execute(final ExecuteCommand executeCommand) {

        val topologyDetails = topologyStore.get(executeCommand.getTopologyId()).orElse(null);
        if(null == topologyDetails) {
            return Optional.empty();
        }
        val topologyName = topologyDetails.getTopology().getName();
        val rId = executeCommand.getRunId();
        val task = topologyDetails
                .getTopology()
                .getTask();
        var currState = EpochTopologyRunState.RUNNING;
        val runInfo
                = runInfoStore.save(new EpochTopologyRunInfo(
                        topologyName,
                        rId,
                        currState,
                        "Job started",
                        new TaskStateElaborator().states(executeCommand, task),
                        new Date(),
                        new Date()))
                .orElse(null);
        if (null == runInfo) {
            return Optional.empty();
        }
        generateRunStateChangedEvent(runInfo);
        try {
            val state = runTopology(executeCommand, topologyDetails, topologyName, rId, runInfo);
            log.info("Run {}/{} completed with result {}", topologyName, rId, state);
            val existingOpt = runInfoStore.get(topologyName, rId);
            return existingOpt
                    .flatMap(existing -> updateRunState(topologyName,
                                                        rId,
                                                        state,
                                                        "Task " + state.name().toLowerCase(),
                                                        existing));
        }
        catch (Exception e) {
            log.error("Error executing task " + topologyName + "/" + rId, e);
            val existingOpt = runInfoStore.get(topologyName, rId);
            return existingOpt
                    .flatMap(existing -> updateRunState(topologyName,
                                  rId,
                                  EpochTopologyRunState.FAILED,
                                  "Task Failed with error: " + e.getMessage(),
                                  existing));
        }

    }


    private EpochTopologyRunState runTopology(
            ExecuteCommand executeCommand,
            EpochTopologyDetails topologyDetails,
            String topologyName,
            String rId,
            EpochTopologyRunInfo runInfo) {
        return switch (topologyDetails.getState()) {
            case ACTIVE -> {
                log.debug("Execution planned for active topology run {}/{}", topologyName, rId);
                yield executeTopology(executeCommand, topologyDetails, runInfo);
            }
            case PAUSED -> {
                log.warn("Execution skipped for paused topology run {}/{}", topologyName, rId);
                yield EpochTopologyRunState.SUCCESSFUL;
            }
            case DELETED -> {
                log.warn("Execution skipped for deleted topology run {}/{}. Will be marked as completed",
                         topologyName, rId);
                yield EpochTopologyRunState.COMPLETED;
            }
        };
    }

    private EpochTopologyRunState executeTopology(
            final ExecuteCommand executeCommand,
            EpochTopologyDetails topologyDetails,
            final EpochTopologyRunInfo runInfo) {
        val task = topologyDetails
                .getTopology()
                .getTask();
        val runId = executeCommand.getRunId();

        val allTaskRunState = task.accept(new TaskExecutor(runId, runInfo, taskEngine, runInfoStore, this));
        return allTaskRunState == EpochTaskRunState.COMPLETED
                     ? EpochTopologyRunState.SUCCESSFUL
                     : EpochTopologyRunState.FAILED;
    }

    private static final class TaskExecutor implements EpochTaskVisitor<EpochTaskRunState> {

        private final String runId;
        private final EpochTopologyRunInfo topologyExecutionInfo;
        private final TaskExecutionEngine taskEngine;
        private final TopologyRunInfoStore runInfoStore;

        private final TopologyExecutorImpl executor;

        private TaskExecutor(
                String runId,
                EpochTopologyRunInfo topologyExecutionInfo,
                TaskExecutionEngine taskEngine,
                TopologyRunInfoStore runInfoStore, TopologyExecutorImpl executor) {
            this.runId = runId;
            this.topologyExecutionInfo = topologyExecutionInfo;
            this.taskEngine = taskEngine;
            this.runInfoStore = runInfoStore;
            this.executor = executor;
        }

        @Override
        public EpochTaskRunState visit(EpochCompositeTask composite) {
            val success = switch (composite.getCompositionType()) {
                case ANY -> composite.getTasks()
                        .stream()
                        .anyMatch(task -> task.accept(this) == EpochTaskRunState.COMPLETED);
                case ALL -> composite.getTasks()
                        .stream()
                        .allMatch(task -> task.accept(this) == EpochTaskRunState.COMPLETED);
            };
            return success ? EpochTaskRunState.COMPLETED : EpochTaskRunState.FAILED;
        }

        @Override
        public EpochTaskRunState visit(EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            var status = EpochTaskRunState.FAILED;
            val taskInfo = topologyExecutionInfo.getTasks().get(taskName);
            val existingUpstreamTaskId =
                    Objects.requireNonNullElse(taskInfo.getUpstreamId(),
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
            val context = new TaskExecutionContext(topologyExecutionInfo.getTopologyId(),
                                                   runId,
                                                   containerExecution.getTaskName(),
                                                   existingUpstreamTaskId);
            try {
                val currState = taskInfo.getState();
                status = switch (currState) {
                    case PENDING -> {
                        log.info("Task {}/{}/{} will be executed", topologyName, runId, taskName);
                        yield runTask(context, containerExecution);
                    }
                    case STARTING, RUNNING -> {
                        log.info("Task {}/{}/{} was already running. Will be recovered", topologyName, runId, taskName);
                        yield pollTillTerminalState(context, containerExecution);

                    }
                    default -> currState;
                };
            }
            catch (Exception e) {
                log.error("Task " + topologyName + "/" + runId + "/" + taskName + " failed with error: " + e.getMessage(),
                          e);
            }
            finally {
                topologyExecutionInfo.getTasks().get(taskName).setState(status);
                log.info("Task {}/{}/{} completed with status {}", topologyName, runId, taskName, status);
            }
            executor.generateRunTaskStateChangedEvent(context, status);
            return status;
        }

        private EpochTaskRunState runTask(
                final TaskExecutionContext context,
                final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            var status = EpochTaskRunState.FAILED;
            try {
                val taskData = taskEngine.start(context, containerExecution);
                status = taskData.getState();
                context.setUpstreamTaskId(taskData.getUpstreamId());
                runInfoStore.updateTaskInfo(topologyName, runId, taskName, taskData);
                executor.generateRunTaskStateChangedEvent(context, status);
                if (status.equals(EpochTaskRunState.STARTING) || status.equals(EpochTaskRunState.RUNNING)) {
                    return pollTillTerminalState(context, containerExecution);
                }
                else {
                    log.warn("Task {}/{}/{} is already finished with status {}", topologyName, runId, taskName, status);
                    return status;
                }
            }
            catch (Exception e) {
                log.error("Error starting task " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(),
                          e);
            }
            return EpochTaskRunState.FAILED;
        }

        private EpochTaskRunState pollTillTerminalState(
                final TaskExecutionContext context,
                final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            val retryPolicy = new RetryPolicy<EpochTaskRunState>()
                    .withDelay(Duration.ofSeconds(3))
                    .withMaxRetries(-1)
                    .handle(Exception.class)
                    .handleResultIf(result -> null == result || !EpochTaskRunState.TERMINAL_STATES.contains(result));
            try {
                return Failsafe.with(List.of(retryPolicy))
                        .get(() -> {
                            val status = taskEngine.status(context, containerExecution);
                            try {
                                runInfoStore.updateTaskState(context.getTopologyId(),
                                                             context.getRunId(),
                                                             context.getTaskName(),
                                                             status);
                            }
                            catch (Exception e) {
                                log.error("Error updating state: ", e);
                            }
                            return status;
                        });
            }
            catch (Exception e) {
                log.error("Error determining task status " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(),
                          e);
            }
            return EpochTaskRunState.FAILED;
        }
    }


    private Optional<EpochTopologyRunInfo> updateRunState(
            String topologyName,
            String runId,
            EpochTopologyRunState state,
            String message,
            EpochTopologyRunInfo existing) {
        val updated = runInfoStore.save(new EpochTopologyRunInfo(
                topologyName,
                runId,
                state,
                message,
                existing.getTasks(),
                existing.getCreated(),
                new Date()));
        updated.ifPresent(this::generateRunStateChangedEvent);
        return updated;
    }

    private void generateRunStateChangedEvent(final EpochTopologyRunInfo runInfo) {
        eventBus.publish(EpochStateChangeEvent.builder()
                                 .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                                 .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, runInfo.getTopologyId(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_ID, runInfo.getRunId(),
                                                  StateChangeEventDataTag.NEW_STATE, runInfo.getState()))
                                 .build());
    }

    private void generateRunTaskStateChangedEvent(
            final TaskExecutionContext context,
            EpochTaskRunState taskState) {
        eventBus.publish(EpochStateChangeEvent.builder()
                                 .type(EpochEventType.TOPOLOGY_RUN_TASK_STATE_CHANGED)
                                 .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, context.getTopologyId(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_ID, context.getRunId(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_TASK_ID, context.getTaskName(),
                                                  StateChangeEventDataTag.NEW_STATE, taskState))
                                 .build());
    }
}
