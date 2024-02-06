package com.phonepe.epoch.models.topology;

import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import lombok.experimental.UtilityClass;
import lombok.val;

import static com.cronutils.model.CronType.QUARTZ;

@UtilityClass
public class Validators {

    public boolean validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.isEmpty()) {
            return false;
        }
        try {
            val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
            val parser = new CronParser(cronDefinition);
            parser.parse(cronExpression).validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
