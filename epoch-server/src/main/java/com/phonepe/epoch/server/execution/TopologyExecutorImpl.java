package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTaskVisitor;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.statemanagement.TaskStateElaborator;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
public final class TopologyExecutorImpl implements TopologyExecutor {

    private final TaskExecutionEngine taskEngine;
    private final TopologyRunInfoStore runInfoStore;

    @Inject
    public TopologyExecutorImpl(TaskExecutionEngine taskEngine, TopologyRunInfoStore runInfoStore) {
        this.taskEngine = taskEngine;
        this.runInfoStore = runInfoStore;
    }

    @Override
    public Optional<EpochTopologyRunInfo> execute(final ExecuteCommand executeCommand) {
        val topologyDetails = executeCommand.getTopology();
        val topologyName = topologyDetails.getTopology().getName();
        val rId = executeCommand.getRunId();
        val task = executeCommand.getTopology()
                .getTopology()
                .getTask();
        var currState = EpochTopologyRunState.RUNNING;
        val runInfo
                = runInfoStore.save(new EpochTopologyRunInfo(
                        topologyName,
                        rId,
                        currState,
                        "Job started",
                        new TaskStateElaborator().states(task),
                        new Date(),
                        new Date()))
                .orElse(null);
        try {
            val state = runTopology(executeCommand, topologyDetails, topologyName, rId, runInfo);
            log.info("Run {}/{} completed with result {}", topologyName, rId, state);
            return runInfoStore.save(runInfo.setState(state)
                                      .setMessage("Task " + state.name().toLowerCase())
                                      .setUpdated(new Date()));
        }
        catch (Exception e) {
            log.error("Error executing task " + topologyName + "/" + rId, e);
            return runInfoStore.save(runInfo.setState(EpochTopologyRunState.FAILED)
                                      .setMessage("Task Failed with error: " + e.getMessage())
                                      .setUpdated(new Date()));
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
                yield executeTopology(executeCommand, runInfo);
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
            final ExecuteCommand executeCommand, final EpochTopologyRunInfo runInfo) {
        val task = executeCommand.getTopology()
                .getTopology()
                .getTask();
        val topologyName = executeCommand.getTopology().getTopology().getName();
        val runId = executeCommand.getRunId();

        val allTaskRunState = task.accept(new TaskExecutor(runId, runInfo, taskEngine));
        val result = allTaskRunState == EpochTaskRunState.COMPLETED
               ? EpochTopologyRunState.SUCCESSFUL
               : EpochTopologyRunState.FAILED;
        return result;
    }

    private static final class TaskExecutor implements EpochTaskVisitor<EpochTaskRunState> {

        private final String runId;
        private final EpochTopologyRunInfo topologyExecutionInfo;
        private final TaskExecutionEngine taskEngine;

        private TaskExecutor(
                String runId,
                EpochTopologyRunInfo topologyExecutionInfo,
                TaskExecutionEngine taskEngine) {
            this.runId = runId;
            this.topologyExecutionInfo = topologyExecutionInfo;
            this.taskEngine = taskEngine;
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
            val topologyName = topologyExecutionInfo.getTopologyName();
            val taskName = containerExecution.getTaskName();
            var status = EpochTaskRunState.FAILED;
            try {
                val currState = topologyExecutionInfo.getTaskStates().get(taskName);
                status = switch (currState) {
                    case NOT_STARTED -> {
                        log.info("Task {}/{}/{} will be executed", topologyName, runId, taskName);
                        yield runTask(containerExecution);
                    }
                    case RUNNING -> {
                        log.info("Task {}/{}/{} was already running. Will be recovered", topologyName, runId, taskName);
                        yield pollTillTerminalState(containerExecution);

                    }
                    default -> currState;
                };
                return status;
            }
            catch (Exception e) {
                log.error("Task " + topologyName + "/" + runId + "/" + taskName + " failed with error: " + e.getMessage(), e);
            }
            finally {
                topologyExecutionInfo.getTaskStates().put(taskName, status);
                log.info("Task {}/{}/{} completed with status {}", topologyName, runId, taskName, status);
            }
            return status;
        }

        private EpochTaskRunState runTask(final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyName();
            val taskName = containerExecution.getTaskName();
            var status = EpochTaskRunState.FAILED;
            try {
                status = taskEngine.start(this.runId, containerExecution);
                if(status.equals(EpochTaskRunState.RUNNING)) {
                    return pollTillTerminalState(containerExecution);
                }
                else {
                    log.warn("Task {}/{}/{} is already finished with status {}", topologyName, runId, taskName, status);
                    return status;
                }
            }
            catch (Exception e) {
                log.error("Error starting task " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(), e);
            }
            return EpochTaskRunState.FAILED;
        }

        private EpochTaskRunState pollTillTerminalState(final EpochContainerExecutionTask containerExecution) {
            val topologyName = topologyExecutionInfo.getTopologyName();
            val taskName = containerExecution.getTaskName();
            val retryPolicy = new RetryPolicy<EpochTaskRunState>()
                    .withBackoff(30, 60, ChronoUnit.SECONDS)
                    .handle(Exception.class)
                    .handleResultIf(result -> null == result || result.equals(EpochTaskRunState.RUNNING));
            try {
                return Failsafe.with(List.of(retryPolicy))
                        .get(() -> taskEngine.status(runId, containerExecution));
            }
            catch (Exception e) {
                log.error("Error determining task status " + topologyName + "/" + runId + "/" + taskName + ": " + e.getMessage(), e);
            }
            return EpochTaskRunState.FAILED;
        }
    }
}
