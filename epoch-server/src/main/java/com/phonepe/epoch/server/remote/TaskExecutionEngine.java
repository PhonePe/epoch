package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;

/**
 *
 */
public interface TaskExecutionEngine {
    EpochTaskRunState start(TaskExecutionContext context, final EpochContainerExecutionTask executionTask);
    EpochTaskRunState status(TaskExecutionContext context, final EpochContainerExecutionTask executionTask);
    boolean cleanup(TaskExecutionContext context, EpochContainerExecutionTask containerExecution);
}
