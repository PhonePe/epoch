package com.phonepe.epoch.server.execution;

import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class ExecuteCommand {
    String runId;
    Date nextExecutionTime;
    String topologyId;
    boolean nextRunNeeded;

    boolean instantRun;
}
