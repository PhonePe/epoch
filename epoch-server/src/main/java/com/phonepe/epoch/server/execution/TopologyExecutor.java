package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;

import java.util.Optional;

/**
 *
 */
public interface TopologyExecutor {
    Optional<EpochTopologyRunInfo> execute(ExecuteCommand executeCommand);
}
