package com.phonepe.epoch.server.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.utils.ZkUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.phonepe.epoch.server.utils.EpochUtils.detailsFrom;

/**
 *
 */
@Singleton
public class ZkTopologyStore implements TopologyStore {
    private static final String DATA_PATH = "/topologies";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkTopologyStore(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public Optional<EpochTopologyDetails> save(EpochTopology spec) {
        val details = detailsFrom(spec);
        val id = details.getId();
        return saveTopology(details, id);
    }

    @Override
    public Optional<EpochTopologyDetails> get(String id) {
        return Optional.ofNullable(ZkUtils.readNodeData(curatorFramework,
                                                        path(id),
                                                        mapper,
                                                        EpochTopologyDetails.class));
    }

    @Override
    @SneakyThrows
    public List<EpochTopologyDetails> list(Predicate<EpochTopologyDetails> filter) {
        return ZkUtils.readChildrenNodes(curatorFramework, DATA_PATH, 0, Integer.MAX_VALUE,
                                         id -> {
                                             val child = ZkUtils.readNodeData(curatorFramework,
                                                                              path(id),
                                                                              mapper,
                                                                              EpochTopologyDetails.class);
                                             return filter.test(child) ? child : null;
                                         });
    }

    @Override
    public Optional<EpochTopologyDetails> updateState(String id, EpochTopologyState state) {
        val updated = get(id)
                .map(old -> new EpochTopologyDetails(old.getId(),
                                                     old.getTopology(),
                                                     state,
                                                     old.getCreated(),
                                                     new Date()))
                .orElse(null);
        if(null == updated) {
            return Optional.empty();
        }
        return saveTopology(updated, id);
    }

    @Override
    public boolean delete(String id) {
        return ZkUtils.deleteNode(curatorFramework, path(id));
    }

    private static String path(String id) {
        return DATA_PATH + "/" + id;
    }

    private Optional<EpochTopologyDetails> saveTopology(EpochTopologyDetails details, String id) {
        return ZkUtils.setNodeData(curatorFramework, path(id), mapper, details)
               ? get(id)
               : Optional.empty();
    }
}
