package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.execution.TaskStatusData;

/**
 *
 */

public interface TaskExecutionEngine {

    EpochTopologyRunTaskInfo start(TaskExecutionContext context, final EpochContainerExecutionTask executionTask);

    TaskStatusData status(TaskExecutionContext context, final EpochContainerExecutionTask executionTask);

    @SuppressWarnings("unused")
    default boolean cleanup(TaskExecutionContext context, EpochContainerExecutionTask containerExecution) {
        return cleanup(context.getUpstreamTaskId());
    }

    default boolean cleanup(EpochTopologyRunInfo runInfo) {
        return runInfo.getTasks()
                .values()
                .stream()
                .map(info -> info.getUpstreamId())
                .allMatch(this::cleanup);
    }

    boolean cleanup(final String upstreamTaskId);
}
