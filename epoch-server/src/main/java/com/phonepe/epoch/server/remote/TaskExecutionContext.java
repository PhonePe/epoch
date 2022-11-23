package com.phonepe.epoch.server.remote;

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
    String upstreamTaskId;
}
