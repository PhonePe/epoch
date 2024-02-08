package com.phonepe.epoch.server.execution;

import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.experimental.UtilityClass;

import static com.cronutils.model.CronType.QUARTZ;

@UtilityClass
public class QuartzCronUtility {

    private static final CronParser CRON_PARSER = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));

    public boolean isValidCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.isEmpty()) {
            return false;
        }
        try {
            CRON_PARSER.parse(cronExpression).validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ExecutionTime getDurationToNextExecution(String cronExpression) {
        return ExecutionTime.forCron(CRON_PARSER.parse(cronExpression));
    }
}
