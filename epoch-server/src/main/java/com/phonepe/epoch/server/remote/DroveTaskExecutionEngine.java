package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;

/**
 *
 */
public class DroveTaskExecutionEngine implements TaskExecutionEngine {
    @Override
    public EpochTaskRunState start(String runId, EpochContainerExecutionTask executionTask) {
        return EpochTaskRunState.RUNNING;
    }

    @Override
    public EpochTaskRunState status(String runId, EpochContainerExecutionTask executionTask) {
        return EpochTaskRunState.COMPLETED;
    }
}
