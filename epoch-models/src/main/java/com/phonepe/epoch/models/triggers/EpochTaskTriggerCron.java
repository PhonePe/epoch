package com.phonepe.epoch.models.triggers;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EpochTaskTriggerCron extends EpochTaskTrigger {

    /*@Range(min = 0, max = 59, message = "Minute of hour can be (0-59).")
    Short minute;
    @Range(min = 0, max = 23, message = "Hour of day can be (0-23).")
    Short hour;
    @Range(min = 1, max = 31, message = "Day of month can be (1-31).")
    Short dayOfMonth;
    @Range(min = 1, max = 12, message = "Month of year can be (1-12).")
    Short month;
    @Range(min = 0, max = 6, message = "Day of week can be (0-6). 0 is sunday")
    Short dayOfWeek;

    @Jacksonized
    @Builder
    public EpochTaskTriggerCron(
            Short minute,
            Short hour,
            Short dayOfMonth,
            Short month,
            Short dayOfWeek) {
        super(EpochTaskTriggerType.CRON);
        this.minute = minute;
        this.hour = hour;
        this.dayOfMonth = dayOfMonth;
        this.month = month;
        this.dayOfWeek = dayOfWeek;
    }*/

    @NotEmpty
    String timeSpec;

    public EpochTaskTriggerCron(String timeSpec) {
        super(EpochTaskTriggerType.CRON);
        this.timeSpec = timeSpec;
    }

    @Override
    public <T> T accept(EpochTriggerVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
