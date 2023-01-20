package com.phonepe.epoch.server.remote;

import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 *
 */
@Data
@AllArgsConstructor
public class TaskExecutionContext{
    String topologyId;
    String runId;
    String taskName;
    EpochTopologyRunType runType;
    String upstreamTaskId;

    public String printId() {
        return topologyId + "/" + runId + "/" + taskName;
    }
}
