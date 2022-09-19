package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import lombok.val;

import java.util.*;

/**
 *
 */
public interface TopologyRunInfoStore {
    Optional<EpochTopologyRunInfo> save(final EpochTopologyRunInfo executionInfo);

    Optional<EpochTopologyRunInfo> get(String topologyName, String runId);

    Collection<EpochTopologyRunInfo> list(String topologyName);

    default Optional<EpochTopologyRunInfo> updateTaskState(
            String topologyName,
            String runId,
            String taskName,
            EpochTaskRunState state) {
        return get(topologyName, runId)
                .flatMap(old -> {
                    val states = new HashMap<>(old.getTaskStates());
                    states.put(taskName, state);
                    return save(new EpochTopologyRunInfo(topologyName,
                                                         runId,
                                                         old.getState(),
                                                         old.getMessage(),
                                                         states,
                                                         old.getCreated(),
                                                         new Date()));
                });
    }
}
