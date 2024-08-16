package com.phonepe.epoch.server;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.notification.NotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.models.triggers.EpochTriggerVisitor;
import com.phonepe.epoch.server.managed.LeadershipManager;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@UtilityClass
public class TestUtils {
    public static void delay(final java.time.Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        await()
                .pollDelay(java.time.Duration.ofMillis(10))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }

    public static void waitUntil(final Callable<Boolean> condition) {
        waitUntil(condition, java.time.Duration.ofMinutes(3));
    }

    public static void waitUntil(final Callable<Boolean> condition, final java.time.Duration duration) {
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
                                               List.of(),
                                               LocalLoggingSpec.DEFAULT,
                                               new AnyPlacementPolicy(),
                                               Map.of(),
                                               Map.of(),
                                               null);
    }

    public static EpochTopologyRunInfo genRunInfo(int index, EpochTopologyRunState state) {
        return genRunInfo("TEST_TOPO", index, state, EpochTaskRunState.RUNNING);
    }

    public static EpochTopologyRunInfo genRunInfo(String topologyId,
                                                  int index,
                                                  EpochTopologyRunState state,
                                                  EpochTaskRunState taskState) {
        return new EpochTopologyRunInfo(
                topologyId,
                "TR-" + index,
                state,
                "Test",
                Map.of("TR-T-" + index,
                       new EpochTopologyRunTaskInfo()
                               .setTaskId("TR-T-" + index)
                               .setState(taskState)
                               .setUpstreamId("TDT-" + index)),
                EpochTopologyRunType.INSTANT,
                new Date(),
                new Date());
    }

    public static String getTimeSpec(EpochTaskTrigger trigger) {
        return trigger.accept(new EpochTriggerVisitor<>() {
            @Override
            public String visit(final EpochTaskTriggerAt at) {
                return null;
            }

            @Override
            public String visit(final EpochTaskTriggerCron cron) {
                return cron.getTimeSpec();
            }
        });
    }

    public static LeadershipManager createLeadershipManager(boolean initialValue) {
        val lm = mock(LeadershipManager.class);
        val ls = new ConsumingFireForgetSignal<Boolean>();
        when(lm.onLeadershipStateChange()).thenReturn(ls);
        val leader = new AtomicBoolean(initialValue);
        when(lm.isLeader()).thenReturn(leader.get());
        return lm;
    }

    public static EpochTopology generateTopologyDesc(int i) {
        return generateTopologyDesc(i, BlackholeNotificationSpec.DEFAULT);
    }

    public static EpochTopology generateTopologyDesc(int i, NotificationSpec notificationSpec) {
        return new EpochTopology("TEST_TOPO-" + i,
                                 genContainerTask(i),
                                 new EpochTaskTriggerAt(new Date()),
                                 notificationSpec);
    }
}
