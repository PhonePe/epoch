package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.topology.EpochTaskRunState;

/**
 *
 */
public record TaskStatusData(EpochTaskRunState state, String errorMessage) {
}
