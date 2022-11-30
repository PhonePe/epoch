package com.phonepe.epoch.models.topology;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 *
 */
@Data
@AllArgsConstructor
public class EpochTopologyRunInfo {
    String topologyId;
    String runId;
    EpochTopologyRunState state;
    String message;
    Map<String, EpochTopologyRunTaskInfo> tasks;
    Date created;
    Date updated;
}
