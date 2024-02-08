package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.models.triggers.EpochTriggerVisitor;
import lombok.val;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

/**
 *
 */
public class ExecutionTimeCalculator {

    public Optional<Duration> executionTime(final EpochTaskTrigger trigger, final Date currTime) {
        return trigger.accept(new EpochTriggerVisitor<>() {
            @Override
            public Optional<Duration> visit(EpochTaskTriggerAt at) {
                val timeDifference = Math.min(0, currTime.getTime() - at.getTime().getTime());
                return Optional.of(Duration.ofMillis(timeDifference));
            }

            @Override
            public Optional<Duration> visit(EpochTaskTriggerCron cron) {
                val executionTime = QuartzCronUtility.getDurationToNextExecution(cron.getTimeSpec());
                return executionTime.timeToNextExecution(ZonedDateTime.ofInstant(currTime.toInstant(),
                                                                                 ZoneId.systemDefault()));
            }
        });
    }
}
