package com.phonepe.epoch.server.event;

/**
 *
 */
public interface EpochEventVisitor<T> {
    T visit(EpochStateChangeEvent stateChangeEvent);
}
