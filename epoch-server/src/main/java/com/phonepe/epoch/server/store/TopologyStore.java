package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
public interface TopologyStore {
    Optional<EpochTopologyDetails> save(final EpochTopology spec);
    Optional<EpochTopologyDetails> get(final String id);
    List<EpochTopologyDetails> list(Predicate<EpochTopologyDetails> filter);
    Optional<EpochTopologyDetails> update(final String id, final EpochTopology topology, final EpochTopologyState state);
    boolean delete(final String id);
}
