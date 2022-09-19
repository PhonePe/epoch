package com.phonepe.epoch.models.tasks;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 *
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CONTAINER_EXECUTION", value = EpochContainerExecutionTask.class),
        @JsonSubTypes.Type(name = "COMPOSITE", value = EpochCompositeTask.class),
})
public abstract class EpochTask {
    @NotNull(message = "Please specify task type")
    private final EpochTaskType type;

    public abstract <T> T accept(final EpochTaskVisitor<T> visitor);
}
