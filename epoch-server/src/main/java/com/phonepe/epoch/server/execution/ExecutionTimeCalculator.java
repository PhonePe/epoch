package com.phonepe.epoch.server.execution;

import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
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

import static com.cronutils.model.CronType.QUARTZ;

/**
 *
 */
public class ExecutionTimeCalculator {
    public Optional<Duration> executionTime(final EpochTaskTrigger trigger, final Date currTime) {
        return trigger.accept(new EpochTriggerVisitor<Optional<Duration>>() {
            @Override
            public Optional<Duration> visit(EpochTaskTriggerAt at) {
                if (currTime.after(at.getTime())) {
                    return Optional.empty();
                }
                return Optional.of(Duration.ofMillis(currTime.getTime() - at.getTime().getTime()));
            }

            @Override
            public Optional<Duration> visit(EpochTaskTriggerCron cron) {

                val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
                val parser = new CronParser(cronDefinition);
                val executionTime = ExecutionTime.forCron(parser.parse(cron.getTimeSpec()));
                return executionTime.timeToNextExecution(ZonedDateTime.ofInstant(currTime.toInstant(), ZoneId.systemDefault()));
            }
        });
    }
}
