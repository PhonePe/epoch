package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import lombok.val;

import javax.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;

/**
 *
 */
@Singleton
public class InMemoryTopologyStore implements TopologyStore {
    private final Map<String, EpochTopologyDetails> data = new ConcurrentHashMap<>();

    @Override
    public Optional<EpochTopologyDetails> save(EpochTopology spec) {
        val id = topologyId(spec.getName());
        return Optional.of(data.compute(id,
                                        (tid, old) -> new EpochTopologyDetails(id,
                                                                               spec,
                                                                               EpochTopologyState.ACTIVE,
                                                                               old != null ? old.getCreated()
                                                                                           : new Date(),
                                                                               new Date())));
    }

    @Override
    public Optional<EpochTopologyDetails> get(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @SuppressWarnings("java:S3958") //Sonar bug
    @Override
    public List<EpochTopologyDetails> list(Predicate<EpochTopologyDetails> filter) {
        return data.values()
                .stream()
                .filter(filter)
                .toList();
    }

    @Override
    public Optional<EpochTopologyDetails> update(final String id, final EpochTopology topology,
                                                 final EpochTopologyState state) {
        return Optional.ofNullable(data.computeIfPresent(id,
                                                         (tid, old) -> new EpochTopologyDetails(old.getId(),
                                                                                                topology,
                                                                                                state,
                                                                                                old.getCreated(),
                                                                                                new Date())));
    }

    @Override
    public boolean delete(String id) {
        return data.remove(id) != null;
    }
}
