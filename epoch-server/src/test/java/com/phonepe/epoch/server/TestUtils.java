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
import lombok.val;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 *
 */
@UtilityClass
public class TestUtils {
    public void delay(final java.time.Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        await()
                .pollDelay(java.time.Duration.ofMillis(10))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }

    public void waitUntil(final Callable<Boolean> condition) {
        waitUntil(condition, java.time.Duration.ofMinutes(3));
    }

    public void waitUntil(final Callable<Boolean> condition, final java.time.Duration duration) {
        await()
                .pollDelay(java.time.Duration.ofSeconds(1))
                .timeout(duration)
                .until(condition);
    }

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
