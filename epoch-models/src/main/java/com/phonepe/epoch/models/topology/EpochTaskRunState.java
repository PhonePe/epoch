package com.phonepe.epoch.models.topology;

import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public enum EpochTaskRunState {
    PENDING,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED
    ;

    public static Set<EpochTaskRunState> TERMINAL_STATES = EnumSet.of(COMPLETED, FAILED);
}
