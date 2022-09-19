package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;

/**
 *
 */
public interface TaskExecutionEngine {
    EpochTaskRunState start(String runId, final EpochContainerExecutionTask executionTask);
    EpochTaskRunState status(String runId, final EpochContainerExecutionTask executionTask);
}
