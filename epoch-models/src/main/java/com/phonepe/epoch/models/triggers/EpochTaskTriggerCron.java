package com.phonepe.epoch.models.triggers;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EpochTaskTriggerCron extends EpochTaskTrigger {

    @NotEmpty
    String timeSpec;

    @Builder
    @Jacksonized
    public EpochTaskTriggerCron(String timeSpec) {
        super(EpochTaskTriggerType.CRON);
        this.timeSpec = timeSpec;
    }

    @Override
    public <T> T accept(EpochTriggerVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
