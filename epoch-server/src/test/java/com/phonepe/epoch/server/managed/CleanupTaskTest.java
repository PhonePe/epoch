package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.config.EpochOptionsConfig;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.InMemoryTopologyRunInfoStore;
import com.phonepe.epoch.server.store.InMemoryTopologyStore;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CleanupTaskTest extends TestBase {

    @Test
    @SneakyThrows
    void testCleanup() {
        val lm = mock(LeadershipManager.class);
        val s = new ConsumingFireForgetSignal<Boolean>();
        val leader = new AtomicBoolean();
        when(lm.isLeader()).thenAnswer((Answer<Boolean>) invocationOnMock -> leader.get());
        when(lm.onLeadershipStateChange()).thenReturn(s);
        val tis = new InMemoryTopologyStore();
        val ris = new InMemoryTopologyRunInfoStore();
        val te = mock(TaskExecutionEngine.class);
        when(te.cleanup(any(EpochTopologyRunInfo.class)))
                .thenAnswer(new Answer<Boolean>() {
                    final AtomicInteger ctr = new AtomicInteger();
                    @Override
                    public Boolean answer(InvocationOnMock invocationOnMock) {
                        return ctr.incrementAndGet() % 2 == 0;
                    }
                });

        val options = new EpochOptionsConfig()
                .setNumRunsPerJob(5)
                .setCleanupJobInterval(Duration.seconds(1));
        val cleanupTask = new CleanupTask(options, lm, tis, ris, te);
        val oldDate = new Date(System.currentTimeMillis() - 120_000);
        IntStream.rangeClosed(1, 105)
                .forEach(i -> tis.save(new EpochTopology("TID-" + i,
                                                         new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                                       .<EpochTask>mapToObj(
                                                                                               TestUtils::genContainerTask)
                                                                                       .toList(),
                                                                               EpochCompositeTask.CompositionType.ALL),
                                                         new EpochTaskTriggerCron("0/2 * * ? * * *"),
                                                         BlackholeNotificationSpec.DEFAULT)));

        IntStream.rangeClosed(1, 100)
                .forEach(i -> IntStream.rangeClosed(1, 25)
                        .forEach(j -> ris.save(new EpochTopologyRunInfo("TID-" + i,
                                                                        "RID-" + j,
                                                                        EpochTopologyRunState.COMPLETED,
                                                                        "",
                                                                        Map.of("TT_1",
                                                                               new EpochTopologyRunTaskInfo().setUpstreamId(
                                                                                               "")
                                                                                       .setState(
                                                                                               EpochTaskRunState.COMPLETED)),
                                                                        EpochTopologyRunType.SCHEDULED,
                                                                        oldDate,
                                                                        oldDate))));
        cleanupTask.start();
        TestUtils.delay(java.time.Duration.ofSeconds(5));
        IntStream.rangeClosed(1, 100)
                .forEach(i -> {
                    val remaining = IntStream.rangeClosed(1, 25)
                            .mapToObj(j -> ris.get("TID-" + i, "RID-" + j).orElse(null))
                            .filter(Objects::nonNull)
                            .toList();
                    assertEquals(25, remaining.size());
                });

        leader.set(true);
        TestUtils.delay(java.time.Duration.ofSeconds(5));
        IntStream.rangeClosed(1, 105)
                .forEach(i -> {
                    val remaining = IntStream.rangeClosed(1, 25)
                            .mapToObj(j -> ris.get("TID-" + i, "RID-" + j).orElse(null))
                            .filter(Objects::nonNull)
                            .toList();
                    if(i <= 100) {
                        assertEquals(5, remaining.size());
                    }
                    else {
                        assertTrue(remaining.isEmpty());
                    }
                });
        cleanupTask.stop();
    }
}