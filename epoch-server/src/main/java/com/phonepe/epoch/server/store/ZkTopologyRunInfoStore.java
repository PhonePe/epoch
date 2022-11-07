package com.phonepe.epoch.server.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.server.utils.ZkUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
@Singleton
public class ZkTopologyRunInfoStore implements TopologyRunInfoStore {
    private static final String DATA_PATH = "/runs";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkTopologyRunInfoStore(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public Optional<EpochTopologyRunInfo> save(EpochTopologyRunInfo executionInfo) {
        val topologyId = executionInfo.getTopologyId();
        val runId = executionInfo.getRunId();
        return ZkUtils.setNodeData(curatorFramework, path(topologyId, runId), mapper, executionInfo)
               ? get(topologyId, runId)
               : Optional.empty();
    }

    @Override
    public Optional<EpochTopologyRunInfo> get(String topologyId, String runId) {
        return Optional.ofNullable(ZkUtils.readNodeData(curatorFramework,
                                                        path(topologyId, runId),
                                                        mapper,
                                                        EpochTopologyRunInfo.class));
    }

    @Override
    public boolean delete(String topologyId, String runId) {
        return ZkUtils.deleteNode(curatorFramework, path(topologyId, runId));
    }

    @Override
    public boolean deleteAll(String topologyId) {
        return ZkUtils.deleteNode(curatorFramework, DATA_PATH + "/" + topologyId);
    }

    @Override
    @SneakyThrows
    public Collection<EpochTopologyRunInfo> list(String topologyId, Predicate<EpochTopologyRunInfo> filter) {
        return ZkUtils.readChildrenNodes(
                curatorFramework,
                DATA_PATH + "/" + topologyId,
                0,
                Integer.MAX_VALUE,
                runId -> {
                    val data = ZkUtils.readNodeData(curatorFramework,
                                                    path(topologyId, runId),
                                                    mapper,
                                                    EpochTopologyRunInfo.class);
                    return filter.test(data) ? data : null;
                });
    }

    private String path(String topologyId, String runId) {
        return DATA_PATH + "/" + topologyId + "/" + runId;
    }
}
