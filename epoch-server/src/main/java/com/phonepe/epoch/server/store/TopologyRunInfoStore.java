package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.utils.EpochUtils;

import java.util.Collection;
import java.util.Date;
import java.util.Optional;
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

    default Optional<EpochTopologyRunInfo> updateTaskInfo(
            String topologyId,
            String runId,
            String taskName,
            EpochTopologyRunTaskInfo taskInfo) {
        return get(topologyId, runId)
                .flatMap(old -> save(new EpochTopologyRunInfo(
                        topologyId,
                        runId,
                        old.getState(),
                        old.getMessage(),
                        EpochUtils.updateTaskInfo(old,
                                                  taskName,
                                                  existing -> existing.setTaskId(taskInfo.getTaskId())
                                                                  .setState(taskInfo.getState())
                                                                  .setUpstreamId(taskInfo.getUpstreamId())),
                        old.getCreated(),
                        new Date())));
    }

    default Optional<EpochTopologyRunInfo> updateTaskState(
            String topologyId,
            String runId,
            String taskName,
            EpochTaskRunState state) {
        return get(topologyId, runId)
                .flatMap(old -> save(new EpochTopologyRunInfo(topologyId,
                                                              runId,
                                                              old.getState(),
                                                              old.getMessage(),
                                                              EpochUtils.addTaskState(old, taskName, state),
                                                              old.getCreated(),
                                                              new Date())));
    }
}
