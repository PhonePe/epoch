package com.phonepe.epoch.models.topology;

import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public enum EpochTaskRunState {
    PENDING,
    STARTING,
    UNKNOWN,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
    ;

    public static final Set<EpochTaskRunState> TERMINAL_STATES = EnumSet.of(COMPLETED, FAILED, CANCELLED);
}
