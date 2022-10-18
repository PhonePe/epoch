package com.phonepe.epoch.server.remote;

import lombok.Value;

/**
 *
 */
@Value
public class TaskExecutionContext{
    String topologyId;
    String runId;
    String taskName;
}
