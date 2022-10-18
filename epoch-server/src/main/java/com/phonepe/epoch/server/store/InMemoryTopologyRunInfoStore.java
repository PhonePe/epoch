package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
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
    public Collection<EpochTopologyRunInfo> list(String topologyId, Predicate<EpochTopologyRunInfo> filter) {
        return data.getOrDefault(topologyId, new HashMap<>()).values()
                .stream()
                .filter(filter)
                .toList();
    }
}
