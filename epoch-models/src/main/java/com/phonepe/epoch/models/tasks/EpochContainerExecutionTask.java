package com.phonepe.epoch.models.tasks;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.config.ConfigSpec;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class EpochContainerExecutionTask extends EpochTask {

    String taskName;
    @NotNull(message = "- Executable details is required")
    @Valid
    ExecutableCoordinates executable;

    @NotEmpty(message = "- CPU/Memory requirements must be specified")
    List<ResourceRequirement> resources;

    @Valid
    List<MountedVolume> volumes;

    @Valid
    List<ConfigSpec> configs;

    @Valid
    LoggingSpec logging;

    PlacementPolicy placementPolicy;

    Map<String, String> tags;

    Map<String, String> env;

    @Size(max = 2048)
    List<String> args;

    @SuppressWarnings("java:S107") //Model class needs required params
    public EpochContainerExecutionTask(
            String taskName,
            ExecutableCoordinates executable,
            List<ResourceRequirement> resources,
            List<MountedVolume> volumes, List<ConfigSpec> configs,
            LoggingSpec logging,
            PlacementPolicy placementPolicy,
            Map<String, String> tags,
            Map<String, String> env, List<String> args) {
        super(EpochTaskType.CONTAINER_EXECUTION);
        this.taskName = taskName;
        this.executable = executable;
        this.resources = resources;
        this.volumes = volumes;
        this.configs = configs;
        this.logging = logging;
        this.placementPolicy = placementPolicy;
        this.tags = tags;
        this.env = env;
        this.args = args;
    }


    @Override
    public <T> T accept(EpochTaskVisitor<T> visitor) {
        return visitor.visit(this);
    }


}
