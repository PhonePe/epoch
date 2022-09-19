package com.phonepe.epoch.models.tasks;

/**
 *
 */
public interface EpochTaskVisitor<T> {
    T visit(final EpochCompositeTask composite);

    T visit(final EpochContainerExecutionTask containerExecution);
}
