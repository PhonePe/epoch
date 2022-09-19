package com.phonepe.epoch.models.tasks;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EpochCompositeTask extends EpochTask {
    public enum CompositionType {
        ALL,
        ANY
    }
    @NotEmpty
    List<EpochTask> tasks;

    CompositionType compositionType;

    @Jacksonized
    @Builder
    public EpochCompositeTask(List<EpochTask> tasks, CompositionType compositionType) {
        super(EpochTaskType.COMPOSITE);
        this.tasks = tasks;
        this.compositionType = Objects.requireNonNullElse(compositionType, CompositionType.ALL);
    }

    @Override
    public <T> T accept(EpochTaskVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
