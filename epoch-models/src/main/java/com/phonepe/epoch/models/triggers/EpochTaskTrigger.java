package com.phonepe.epoch.models.triggers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 *
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "AT", value = EpochTaskTriggerAt.class),
        @JsonSubTypes.Type(name = "CRON", value = EpochTaskTriggerCron.class),
})
public abstract class EpochTaskTrigger {
    private final EpochTaskTriggerType type;

    public abstract <T> T accept(final EpochTriggerVisitor<T> visitor);
}
