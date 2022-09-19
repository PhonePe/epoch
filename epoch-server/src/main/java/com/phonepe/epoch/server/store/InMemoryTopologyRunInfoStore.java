package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import lombok.val;

import java.util.*;

/**
 *
 */
public class InMemoryTopologyRunInfoStore implements TopologyRunInfoStore {
    private final Map<String, Map<String, EpochTopologyRunInfo>> data = new HashMap<>();

    @Override
    public Optional<EpochTopologyRunInfo> save(EpochTopologyRunInfo executionInfo) {
        val topologyName = executionInfo.getTopologyName();
        return Optional.of(data.compute(topologyName, (tName, old) -> {
                    val instances = null == old ? new HashMap<String, EpochTopologyRunInfo>() : old;
                    instances.put(executionInfo.getRunId(), executionInfo);
                    return instances;
                })
                                   .get(executionInfo.getRunId()));
    }

    @Override
    public Optional<EpochTopologyRunInfo> get(String topologyName, String runId) {
        return Optional.ofNullable(data.getOrDefault(topologyName, new HashMap<>()).get(runId));
    }

    @Override
    public Collection<EpochTopologyRunInfo> list(String topologyName) {
        return data.getOrDefault(topologyName, new HashMap<>()).values();
    }
}
