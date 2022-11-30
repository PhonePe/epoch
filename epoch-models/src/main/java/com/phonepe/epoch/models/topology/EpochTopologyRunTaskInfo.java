package com.phonepe.epoch.models.topology;

import lombok.Data;

/**
 *
 */
@Data
public class EpochTopologyRunTaskInfo {
    public static final String UNKNOWN_TASK_ID="UNKNOWN";
    EpochTaskRunState state;
    String upstreamId;
}
