package com.phonepe.epoch.models.triggers;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EpochTaskTriggerAt extends EpochTaskTrigger {

    Date time;

    @Jacksonized
    @Builder
    public EpochTaskTriggerAt(Date time) {
        super(EpochTaskTriggerType.AT);
        this.time = time;
    }

    public <T> T accept(EpochTriggerVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
