package com.phonepe.epoch.models.topology;

import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Describes topology to be executed, and when it needs to be executed
 */
@Value
public class EpochTopology {

    @NotEmpty(message = "Please provide an understandable name for the topology")
    @Pattern(regexp = "[0-9a-zA-Z_-]", message = "Only alphanumeric - and _ allowed")
    @Size(min = 1, max = 255, message = "Min length is 1, max 255")
    String name;

    @NotNull
    @Valid
    EpochTask task;

    @NotNull
    @Valid
    EpochTaskTrigger trigger;
}
