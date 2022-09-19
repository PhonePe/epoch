package com.phonepe.epoch.models.topology;

import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class EpochTopologyDetails {
    String id;
    EpochTopology topology;
    EpochTopologyState state;
    Date created;
    Date updated;

}
