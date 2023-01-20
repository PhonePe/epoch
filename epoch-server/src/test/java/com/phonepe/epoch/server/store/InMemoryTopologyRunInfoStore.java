package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import lombok.val;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
@Singleton
public class InMemoryTopologyRunInfoStore implements TopologyRunInfoStore {
    private final Map<String, Map<String, EpochTopologyRunInfo>> data = new HashMap<>();

    @Override
    public Optional<EpochTopologyRunInfo> save(EpochTopologyRunInfo executionInfo) {
        val topologyName = executionInfo.getTopologyId();
        return Optional.of(data.compute(topologyName, (tName, old) -> {
                    val instances = null == old ? new HashMap<String, EpochTopologyRunInfo>() : old;
                    instances.put(executionInfo.getRunId(), executionInfo);
                    return instances;
                })
                                   .get(executionInfo.getRunId()));
    }

    @Override
    public Optional<EpochTopologyRunInfo> get(String topologyId, String runId) {
        return Optional.ofNullable(data.getOrDefault(topologyId, new HashMap<>()).get(runId));
    }

    @Override
    public boolean delete(String topologyId, String runId) {
        return data.compute(topologyId,
                            (itd, old) -> {
                                if (old == null) {
                                    return null;
                                }
                                old.remove(runId);
                                if (old.isEmpty()) {
                                    return null;
                                }
                                return old;
                            }) != null;
    }

    @Override
    public boolean deleteAll(String topologyId) {
        return data.remove(topologyId) != null;
    }

    @SuppressWarnings("java:S3958") //Sonar bug
    @Override
    public Collection<EpochTopologyRunInfo> list(String topologyId, Predicate<EpochTopologyRunInfo> filter) {
        return data.getOrDefault(topologyId, new HashMap<>()).values()
                .stream()
                .filter(filter)
                .toList();
    }

    public Optional<EpochTopologyRunInfo> forceTaskState(String topologyId, String runId, String taskId, EpochTaskRunState state) {
        return Optional.ofNullable(data.computeIfPresent(topologyId,
                              (key, runMap) -> {
                                  runMap.computeIfPresent(runId, (runKey, run) -> {
                                      run.getTasks().computeIfPresent(taskId, (taskKey, task) -> task.setState(state));
                                      return run;
                                  });
                                  return runMap;
                              }))
                .flatMap(runMap -> Optional.ofNullable(runMap.get(runId)));
    }
}
