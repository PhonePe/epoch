package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class SchedulerTest {

    @Test
    @SneakyThrows
    void check() {
        val topologyExecutor = mock(TopologyExecutor.class);
        when(topologyExecutor.execute(any(ExecuteCommand.class)))
                .thenAnswer(invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(0, ExecuteCommand.class);
                    return Optional.of(new EpochTopologyRunInfo(
                            cmd.getTopology().getTopology().getName(),
                            cmd.getRunId(),
                            EpochTopologyRunState.SUCCESSFUL,
                            "",
                            Map.of(),
                            new Date(),
                            new Date()
                    ));
                });

        val s = new Scheduler(Executors.newCachedThreadPool(), topologyExecutor);
        val ctr = new AtomicInteger();
        s.taskCompleted().connect(r -> {
            if(r.topologyDetails().getTopology().getName().equals("test-topo-2")) {
                ctr.incrementAndGet();
            }
        });
        s.start();
        s.schedule(detailsFrom(new EpochTopology("test-topo",
                                                 null,
                                                 new EpochTaskTriggerCron("0/2 * * ? * * *"))));
        s.schedule(detailsFrom(new EpochTopology("test-topo-2",
                                                 null,
                                                 new EpochTaskTriggerCron("0/4 * * ? * * *"))));

        Awaitility.await()
                .forever()
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> ctr.get() == 5);
        s.stop();
    }

}