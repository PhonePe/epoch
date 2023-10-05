package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.store.InMemoryTopologyRunInfoStore;
import com.phonepe.epoch.server.store.InMemoryTopologyStore;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class TopologyRecoveryTest extends TestBase {

    @Test
    @SneakyThrows
    void testRecovery() {
        val ts = new InMemoryTopologyStore();
        val topologies = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> ts.save(new EpochTopology("test-topo-" + i,
                                                         new EpochContainerExecutionTask("TEST_TASK",
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null),
                                                         new EpochTaskTriggerCron("0/2 * * ? * * *"),
                                                         BlackholeNotificationSpec.DEFAULT))
                        .flatMap(topology -> i % 2 == 0 ? ts.updateState(topologyId(topology.getTopology()),
                                                                    EpochTopologyState.DELETED)
                                                        : Optional.of(topology))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        val ris = new InMemoryTopologyRunInfoStore();
        topologies.forEach(td -> ris.save(
                new EpochTopologyRunInfo(td.getId(),
                                         "R-001",
                                         EpochTopologyRunState.RUNNING,
                                         "",
                                         Map.of("TEST_TASK", new EpochTopologyRunTaskInfo()
                                                 .setState(EpochTaskRunState.RUNNING)),
                                         EpochTopologyRunType.INSTANT,
                                         new Date(),
                                         new Date())));
        val scheduler = mock(Scheduler.class);
        val recoveryCount = new AtomicInteger();
        val scheduleCount = new AtomicInteger();
        when(scheduler.recover(anyString(), anyString(), anyString(), any(), any()))
                .then(new Answer<Boolean>() {
                    final AtomicInteger idx = new AtomicInteger();

                    @Override
                    public Boolean answer(InvocationOnMock invocationMock) throws Throwable {
                        if (idx.incrementAndGet() % 2 == 0) {
                            recoveryCount.incrementAndGet();
                            return true;
                        }
                        return false;
                    }
                });
        when(scheduler.schedule(anyString(), anyString(), any(), any()))
                .thenAnswer((Answer<Optional<String>>) invocationMock -> {
                    scheduleCount.incrementAndGet();
                    return Optional.of("xx");
                });
        scheduler.start();
        val lm = TestUtils.createLeadershipManager(true);
        val tr = new TopologyRecovery(lm, ts, ris, scheduler);
        tr.start();
        lm.onLeadershipStateChange().dispatch(true);
        TestUtils.waitUntil(() -> scheduleCount.get() == 50);
        assertEquals(25, recoveryCount.get());
        assertEquals(50, scheduleCount.get());
        tr.stop();
        scheduler.stop();
    }
}