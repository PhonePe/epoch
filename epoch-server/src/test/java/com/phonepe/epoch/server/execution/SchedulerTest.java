package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.epoch.server.utils.EpochUtils.detailsFrom;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class SchedulerTest {

    @Test
    @SneakyThrows
    void check() {
        val topo1 = new EpochTopology("test-topo",
                                               null,
                                               new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val topoDetails1 = detailsFrom(topo1);
        val topoId1 = topologyId(topo1);

        val topo2 = new EpochTopology("test-topo-2",
                                               null,
                                               new EpochTaskTriggerCron("0/4 * * ? * * *"));
        val topoDetails2 = detailsFrom(topo2);
        val topoId2 = topologyId(topo2);

        val ts = mock(TopologyStore.class);
        when(ts.get(anyString()))
                .thenAnswer(invocationOnMock -> {
                    val tId = invocationOnMock.getArgument(0, String.class);
                    if(tId.equals(topoId1)) {
                        return Optional.of(topoDetails1);
                    }
                    if(tId.equals(topoId2)) {
                        return Optional.of(topoDetails2);
                    }
                    return Optional.empty();
                });
        val topologyExecutor = mock(TopologyExecutor.class);
        when(topologyExecutor.execute(any(ExecuteCommand.class)))
                .thenAnswer(invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(0, ExecuteCommand.class);
                    return Optional.of(new EpochTopologyRunInfo(
                            cmd.getTopologyId(),
                            cmd.getRunId(),
                            EpochTopologyRunState.SUCCESSFUL,
                            "",
                            Map.of(),
                            new Date(),
                            new Date()
                    ));
                });

        val s = new Scheduler(Executors.newCachedThreadPool(), ts, topologyExecutor);
        val ctr = new AtomicInteger();
        s.taskCompleted().connect(r -> {
            if(r.topologyId().equals(topologyId("test-topo-2"))) {
                ctr.incrementAndGet();
            }
        });
        s.start();
        val currDate = new Date();
        s.schedule(topoId1, topo1.getTrigger(), currDate);
        s.schedule(topoId2, topo2.getTrigger(), currDate);

        Awaitility.await()
                .forever()
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> ctr.get() == 5);
        s.stop();
    }

}