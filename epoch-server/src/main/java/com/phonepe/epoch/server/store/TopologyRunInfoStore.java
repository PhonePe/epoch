package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import lombok.val;

import java.util.*;
import java.util.function.Predicate;

/**
 *
 */
public interface TopologyRunInfoStore {
    Optional<EpochTopologyRunInfo> save(final EpochTopologyRunInfo executionInfo);

    Optional<EpochTopologyRunInfo> get(String topologyId, String runId);

    boolean delete(String topologyId, String runId);

    boolean deleteAll(String topologyId);

    Collection<EpochTopologyRunInfo> list(String topologyId, Predicate<EpochTopologyRunInfo> filter);

    default Optional<EpochTopologyRunInfo> updateUpstreamId(
            String topologyId,
            String runId,
            String upstreamTaskId) {
        return get(topologyId, runId)
                .flatMap(old -> save(new EpochTopologyRunInfo(topologyId,
                                                          runId,
                                                          upstreamTaskId,
                                                          old.getState(),
                                                          old.getMessage(),
                                                          old.getTaskStates(),
                                                          old.getCreated(),
                                                          new Date())));
    }

    default Optional<EpochTopologyRunInfo> updateTaskState(
            String topologyId,
            String runId,
            String taskName,
            EpochTaskRunState state) {
        return get(topologyId, runId)
                .flatMap(old -> {
                    val states = new HashMap<>(old.getTaskStates());
                    states.put(taskName, state);
                    return save(new EpochTopologyRunInfo(topologyId,
                                                         runId,
                                                         old.getUpstreamTaskId(),
                                                         old.getState(),
                                                         old.getMessage(),
                                                         states,
                                                         old.getCreated(),
                                                         new Date()));
                });
    }
}
