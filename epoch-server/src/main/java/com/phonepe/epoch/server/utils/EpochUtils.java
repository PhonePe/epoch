package com.phonepe.epoch.server.utils;

import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import lombok.experimental.UtilityClass;

import java.util.Date;
import java.util.UUID;

/**
 *
 */
@UtilityClass
public class EpochUtils {
    public static String topologyId(final EpochTopology topology) {
        return UUID.nameUUIDFromBytes(topology.getName().getBytes()).toString();
    }

    public static EpochTopologyDetails detailsFrom(final EpochTopology topology) {
        return new EpochTopologyDetails(topologyId(topology), topology, EpochTopologyState.ACTIVE, new Date(), new Date());
    }
}
