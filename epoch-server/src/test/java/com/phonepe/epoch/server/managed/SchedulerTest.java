package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.execution.ExecuteCommand;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.store.InMemoryTopologyStore;
import io.dropwizard.util.Strings;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.epoch.server.TestUtils.createLeadershipManager;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class SchedulerTest {

    @Test
    @SneakyThrows
    void testScheduledTopologyRun() {
        val topo1 = new EpochTopology("test-topo",
                                      null,
                                      new EpochTaskTriggerCron("0/5 * * ? * * *"),
                                      BlackholeNotificationSpec.DEFAULT);
        val topoId1 = topologyId(topo1);

        val topo2 = new EpochTopology("test-topo-2",
                                      null,
                                      new EpochTaskTriggerCron("0/5 * * ? * * *"),
                                      BlackholeNotificationSpec.DEFAULT);
        val topoId2 = topologyId(topo2);

        val ts = new InMemoryTopologyStore();
        ts.save(topo1);
        ts.save(topo2);
        final TopologyExecutor topologyExecutor = createExecutor(EpochTopologyRunState.SUCCESSFUL);
        val lm = createLeadershipManager(true);

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor, lm);
        val ctr = new AtomicInteger();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId2)) {
                ctr.incrementAndGet();
            }
        });
        s.start();
        lm.onLeadershipStateChange().dispatch(null);
        val currDate = new Date();
        s.schedule(topoId1, topo1.getTrigger(), currDate);
        s.schedule(topoId2, topo2.getTrigger(), currDate);
        TestUtils.waitUntil(() -> ctr.get() == 3);
        assertEquals(3, ctr.get());
        s.stop();
    }


    @Test
    @SneakyThrows
    void testScheduledTopologyInstant() {
        val topo = new EpochTopology("test-topo",
                                     null,
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"),
                                     BlackholeNotificationSpec.DEFAULT);
        val topoId = topologyId(topo);

        val ts = new InMemoryTopologyStore();
        ts.save(topo);
        val topologyExecutor = createExecutor(EpochTopologyRunState.COMPLETED);
        val lm = createLeadershipManager(true);

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor, lm);
        val runCompleted = new AtomicBoolean();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId)) {
                runCompleted.set(true);
            }
        });
        s.start();
        lm.onLeadershipStateChange().dispatch(null);
        assertFalse(Strings.isNullOrEmpty(s.scheduleNow(topoId).orElse(null)));
        TestUtils.waitUntil(runCompleted::get);
        assertTrue(runCompleted.get());
        s.stop();
    }

    @Test
    @SneakyThrows
    void testScheduledTopologyAt() {
        val atDate = new Date(System.currentTimeMillis() + 100);
        val topo = new EpochTopology("test-topo",
                                     null,
                                     new EpochTaskTriggerAt(atDate),
                                     BlackholeNotificationSpec.DEFAULT);
        val topoId = topologyId(topo);

        val ts = new InMemoryTopologyStore();
        ts.save(topo);
        val topologyExecutor = createExecutor(EpochTopologyRunState.COMPLETED);
        val lm = createLeadershipManager(true);

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor, lm);
        val runCompleted = new AtomicBoolean();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId)) {
                runCompleted.set(true);
            }
        });
        s.start();
        lm.onLeadershipStateChange().dispatch(true);
        assertTrue(s.schedule(topoId, topo.getTrigger(), new Date()).isPresent());
        TestUtils.waitUntil(runCompleted::get);
        assertTrue(runCompleted.get());
        s.stop();
    }

    @Test
    @SneakyThrows
    void testRecovery() {
        val atDate = new Date(System.currentTimeMillis() + 100);
        val topo = new EpochTopology("test-topo",
                                     null,
                                     new EpochTaskTriggerAt(atDate),
                                     BlackholeNotificationSpec.DEFAULT);
        val topoId = topologyId(topo);

        val ts = new InMemoryTopologyStore();
        ts.save(topo);
        val topologyExecutor = createExecutor(EpochTopologyRunState.COMPLETED);
        val lm = createLeadershipManager(true);

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor, lm);
        val runCompleted = new AtomicBoolean();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId)) {
                runCompleted.set(true);
            }
        });
        s.start();
        lm.onLeadershipStateChange().dispatch(null);
        assertTrue(s.recover(topoId, "r1", new Date(), EpochTopologyRunType.SCHEDULED));
        TestUtils.waitUntil(runCompleted::get);
        assertTrue(runCompleted.get());
        s.stop();
    }

    @Test
    @SneakyThrows
    void testNotLeader() {
        val atDate = new Date(System.currentTimeMillis() + 100);
        val topo = new EpochTopology("test-topo",
                                     null,
                                     new EpochTaskTriggerAt(atDate),
                                     BlackholeNotificationSpec.DEFAULT);
        val topoId = topologyId(topo);

        val ts = new InMemoryTopologyStore();
        ts.save(topo);
        val topologyExecutor = createExecutor(EpochTopologyRunState.COMPLETED);
        val lm = createLeadershipManager(false);

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor, lm);
        val runCompleted = new AtomicBoolean();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId)) {
                runCompleted.set(true);
            }
        });
        s.start();
        assertTrue(s.schedule(topoId, topo.getTrigger(), new Date()).isPresent());
        try {
            TestUtils.waitUntil(runCompleted::get, Duration.ofSeconds(3));
            fail("Should have failed");
        }
        catch (ConditionTimeoutException e) {
            assertFalse(runCompleted.get());
            return;
        }
        finally {
            s.stop();
        }
        fail("Should have failed");
    }

    @Test
    @SneakyThrows
    void testScheduledSchedulingFail() {
        val atDate = new Date(System.currentTimeMillis() + 100);
        val topo = new EpochTopology("test-topo",
                                     null,
                                     new EpochTaskTriggerAt(atDate),
                                     BlackholeNotificationSpec.DEFAULT);
        val topoId = topologyId(topo);

        val ts = new InMemoryTopologyStore();
        ts.save(topo);
        val topologyExecutor = createExecutor(EpochTopologyRunState.COMPLETED);
        val lm = createLeadershipManager(true);
        val ex = mock(ExecutorService.class);
        when(ex.submit(any(Runnable.class)))
                .thenAnswer(new Answer<Future<?>>() {
                    private final AtomicInteger ctr = new AtomicInteger();
                    private final ExecutorService root = Executors.newCachedThreadPool();

                    @Override
                    public Future<?> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        if (ctr.incrementAndGet() == 1) {
                            throw new IllegalStateException("Test exception");
                        }
                        return root.submit((Runnable) invocationOnMock.getArgument(0));
                    }
                });
        val s = new Scheduler(ex, ts, topologyExecutor, lm);
        val runCompleted = new AtomicBoolean();
        s.taskCompleted().connect(r -> {
            if (r.topologyId().equals(topoId)) {
                runCompleted.set(true);
            }
        });
        s.start();
        lm.onLeadershipStateChange().dispatch(true);
        assertTrue(s.schedule(topoId, topo.getTrigger(), new Date()).isPresent());
        TestUtils.waitUntil(runCompleted::get);
        assertTrue(runCompleted.get());
        s.stop();
    }

    private static TopologyExecutor createExecutor(final EpochTopologyRunState finalState) {
        val topologyExecutor = mock(TopologyExecutor.class);
        when(topologyExecutor.execute(any(ExecuteCommand.class)))
                .thenAnswer(invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(0, ExecuteCommand.class);
                    return Optional.of(new EpochTopologyRunInfo(
                            cmd.getTopologyId(),
                            cmd.getRunId(),
                            finalState,
                            "",
                            Map.of(),
                            cmd.getRunType(),
                            new Date(),
                            new Date()
                    ));
                });
        return topologyExecutor;
    }

}