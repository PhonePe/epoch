package com.phonepe.epoch.server;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

/**
 *
 */
@UtilityClass
public class TestUtils {
    public static EpochContainerExecutionTask genContainerTask(int index) {
        return new EpochContainerExecutionTask("test-task-" + index,
                                               new DockerCoordinates(
                                                       "docker.io/santanusinha/perf-test-server" +
                                                               ":0.3",
                                                       Duration.seconds(100)),
                                               List.of(new CPURequirement(1),
                                                       new MemoryRequirement(512)),
                                               List.of(new MountedVolume("/tmp",
                                                                         "/tmp",
                                                                         MountedVolume.MountMode.READ_ONLY)),
                                               LocalLoggingSpec.DEFAULT,
                                               new AnyPlacementPolicy(),
                                               Map.of(),
                                               Map.of());
    }
}
