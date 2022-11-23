package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.topology.EpochTaskRunState;

/**
 *
 */
public record TaskExecutionData(String upstreamTaskId, EpochTaskRunState state) {
    public static final String UNKNOWN_TASK_ID="UNKNOWN";
}
