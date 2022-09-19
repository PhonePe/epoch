package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class ExecuteCommand {
    String runId;
    Date nextExecutionTime;
    EpochTopologyDetails topology;
}
