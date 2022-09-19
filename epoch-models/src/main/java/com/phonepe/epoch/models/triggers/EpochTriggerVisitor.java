package com.phonepe.epoch.models.triggers;

/**
 *
 */
public interface EpochTriggerVisitor<T> {
    T visit(final EpochTaskTriggerAt at);

    T visit(final EpochTaskTriggerCron cron);
}
