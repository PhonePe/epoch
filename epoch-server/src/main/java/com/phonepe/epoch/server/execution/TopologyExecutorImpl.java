package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTaskVisitor;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.models.triggers.EpochTriggerVisitor;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.remote.CancelResponse;
import com.phonepe.epoch.server.remote.TaskExecutionContext;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.statemanagement.TaskStateElaborator;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        if (null == topologyDetails) {
            log.error("Could not get details for topology {}. Cannot proceed.", executeCommand.getTopologyId());
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
                        executeCommand.getRunType(),
                        new Date(),
                        new Date()))
                .orElse(null);
        if (null == runInfo) {
            log.error("Could not save run info for {}/{}. Cannot proceed.", topologyName, rId);
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
            case ACTIVE -> startTopologyRun(executeCommand, topologyDetails, topologyName, rId, runInfo);
            case PAUSED -> switch (executeCommand.getRunType()) {
                case INSTANT -> startTopologyRun(executeCommand, topologyDetails, topologyName, rId, runInfo);
                case SCHEDULED -> {
                    log.warn("Execution skipped for paused topology run {}/{}", topologyName, rId);
                    yield EpochTopologyRunState.SKIPPED;
                }
            };
            case DELETED -> {
                log.warn("Execution skipped for deleted topology run {}/{}. Will be marked as completed",
                         topologyName, rId);
                yield EpochTopologyRunState.COMPLETED;
            }
        };
    }

    private EpochTopologyRunState startTopologyRun(
            ExecuteCommand executeCommand,
            EpochTopologyDetails topologyDetails,
            String topologyName,
            String rId,
            EpochTopologyRunInfo runInfo) {
        log.debug("Execution planned for active topology run {}/{} of type {}",
                  topologyName,
                  rId,
                  runInfo.getRunType());
        val task = topologyDetails
                .getTopology()
                .getTask();
        val runId = executeCommand.getRunId();

        val allTaskRunState = task.accept(new TaskExecutor(runId, runInfo, taskEngine, runInfoStore, this,
                new Date(Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli())));
        return allTaskRunState.state() == EpochTaskRunState.COMPLETED
               ? successState(topologyDetails, executeCommand)
               : EpochTopologyRunState.FAILED;
    }


    private record TaskExecutor(String runId, EpochTopologyRunInfo topologyExecutionInfo,
                                TaskExecutionEngine taskEngine, TopologyRunInfoStore runInfoStore,
                                TopologyExecutorImpl executor,
                                Date stopTime) implements EpochTaskVisitor<TaskStatusData> {

        @Override
        public TaskStatusData visit(EpochCompositeTask composite) {
            val status = switch (composite.getCompositionType()) {
                case ANY -> composite.getTasks()
                        .stream()
                        .map(task -> task.accept(this))
                        .filter(stateData -> stateData.state() == EpochTaskRunState.COMPLETED)
                        .findFirst()
                        .orElse(null);
                case ALL -> {
                    val failed = composite.getTasks()
                            .stream()
                            .map(task -> task.accept(this))
                            .filter(stateData -> stateData.state() == EpochTaskRunState.FAILED
                                    || stateData.state() == EpochTaskRunState.CANCELLED)
                            .findFirst()
                            .orElse(null);
                    yield Objects.requireNonNullElse(failed, new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
                }
            };
            return Objects.requireNonNullElse(status,
                                              new TaskStatusData(EpochTaskRunState.FAILED,
                                                                 "Unknown status for composite task"));
        }
        @Override
        public TaskStatusData visit(EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            val taskInfo = topologyExecutionInfo.getTasks().get(taskName);
            final var context = getTaskExecutionContext(containerExecution, taskInfo);
            try {
                val currState = taskInfo.getState();
                val status = switch (currState) {
                    case PENDING -> {
                        log.info("Task {}/{}/{} will be executed", topologyName, runId, taskName);
                        yield runTask(context, containerExecution);
                    }
                    default -> {
                        log.info("Task {}/{}/{} was already running. Current state is {}. Will be recovered",
                                 topologyName, runId, taskName, currState);
                        yield pollTillTerminalState(context, containerExecution);
                    }
                };
                topologyExecutionInfo.getTasks()
                        .get(taskName)
                        .setState(status.state())
                        .setErrorMessage(status.errorMessage());
                log.info("Task {}/{}/{} completed with status {}", topologyName, runId, taskName, status);
                executor.generateRunTaskStateChangedEvent(context, status);
                return status;
            }
            catch (Exception e) {
                log.error("Task " + topologyName + "/" + runId + "/" + taskName + " failed with error: " + e.getMessage(),
                          e);
                throw e;
            }
        }

        @NotNull
        private TaskExecutionContext getTaskExecutionContext(EpochContainerExecutionTask containerExecution,
                                                             EpochTopologyRunTaskInfo taskInfo) {
            val existingUpstreamTaskId =
                    Objects.requireNonNullElse(taskInfo.getUpstreamId(),
                            EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
            return new TaskExecutionContext(topologyExecutionInfo.getTopologyId(),
                    runId,
                    containerExecution.getTaskName(),
                    topologyExecutionInfo.getRunType(),
                    existingUpstreamTaskId);
        }

        private TaskStatusData runTask(
                final TaskExecutionContext context,
                final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            var status = EpochTaskRunState.FAILED;

            try {
                val taskData = taskEngine.start(context, containerExecution);
                status = taskData.getState();
                context.setUpstreamTaskId(taskData.getUpstreamId());
                val data = runInfoStore.updateTaskInfo(topologyName, runId, taskName, taskData).orElse(null);
                log.trace("Current data state: {}", data);
                val taskStatusData = new TaskStatusData(taskData.getState(), taskData.getErrorMessage());
                executor.generateRunTaskStateChangedEvent(context, taskStatusData);
                if (EpochTaskRunState.TERMINAL_STATES.contains(status)) {
                    log.warn("Task {}/{}/{} is already finished with status {}", topologyName, runId, taskName, status);
                    return taskStatusData;
                }
                else {
                    return pollTillTerminalState(context, containerExecution);
                }
            }
            catch (Exception e) {
                log.error("Error starting task " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(),
                          e);
            }
            return new TaskStatusData(EpochTaskRunState.FAILED, "Unknown task state");
        }

        @SuppressWarnings("java:S1874") //Sonar bug ... unable to detect overload
        private TaskStatusData pollTillTerminalState(
                final TaskExecutionContext context,
                final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyId();
            val taskName = containerExecution.getTaskName();
            val retryPolicy = new RetryPolicy<TaskStatusData>()
                    .withDelay(Duration.ofSeconds(3))
                    .withMaxRetries(-1)
                    .onFailedAttempt(attempt -> {
                        if (null != attempt.getLastFailure()) {
                            log.error("Task status fetch attempt " + attempt.getAttemptCount()
                                              + " for " + context.printId()
                                              + " failed with error: " + attempt.getLastFailure()
                                    .getMessage(), attempt.getLastFailure());
                        }
                        else {
                            log.debug("Task status fetch attempt {}  for {} returned non-terminal state : {}",
                                      attempt.getAttemptCount(), context.printId(), attempt.getLastResult());
                        }
                    })
                    .handleIf(e -> true)
                    .handleResultIf(result -> null == result || !EpochTaskRunState.TERMINAL_STATES.contains(result.state()));
            try {
                return Failsafe.with(List.of(retryPolicy))
                        .get(() -> {
                            if (new Date().after(stopTime)) {
                                CancelResponse cancelResponse = taskEngine.cancelTask(context.getUpstreamTaskId());
                            }
                            val status = taskEngine.status(context, containerExecution);
                            if (null == status) {
                                log.debug("No status received. Task has probably not started yet..");
                                return null;
                            }
                            else {
                                val data = runInfoStore.updateTaskState(context.getTopologyId(),
                                                                        context.getRunId(),
                                                                        context.getTaskName(),
                                                                        status.state(),
                                                                        status.errorMessage())
                                        .orElse(null);
                                log.trace("Data state while polling: {}", data);
                            }
                            return status;
                        });
            }
            catch (Exception e) {
                log.error("Error determining task status " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(),
                          e);
                throw e;
            }
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
                existing.getRunType(),
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
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, runInfo.getRunType(),
                                                  StateChangeEventDataTag.NEW_STATE, runInfo.getState()))
                                 .build());
    }

    private void generateRunTaskStateChangedEvent(
            final TaskExecutionContext context,
            final TaskStatusData status) {
        eventBus.publish(EpochStateChangeEvent.builder()
                                 .type(EpochEventType.TOPOLOGY_RUN_TASK_STATE_CHANGED)
                                 .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID,
                                                  context.getTopologyId(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_ID,
                                                  context.getRunId(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_TASK_ID,
                                                  context.getTaskName(),
                                                  StateChangeEventDataTag.TOPOLOGY_RUN_TYPE,
                                                  context.getRunType(),
                                                  StateChangeEventDataTag.NEW_STATE,
                                                  status.state(),
                                                  StateChangeEventDataTag.ERROR_MESSAGE,
                                                  Objects.requireNonNullElse(status.errorMessage(), "")))
                                 .build());
    }

    private EpochTopologyRunState successState(final EpochTopologyDetails topologyDetails, final ExecuteCommand command) {
        return switch (command.getRunType()) {
            case SCHEDULED -> topologyDetails.getTopology().getTrigger().accept(new EpochTriggerVisitor<EpochTopologyRunState>() {
                @Override
                public EpochTopologyRunState visit(EpochTaskTriggerAt at) {
                    return EpochTopologyRunState.COMPLETED;
                }

                @Override
                public EpochTopologyRunState visit(EpochTaskTriggerCron cron) {
                    return EpochTopologyRunState.SUCCESSFUL;
                }
            });
            case INSTANT -> EpochTopologyRunState.COMPLETED;
        };
    }
}
