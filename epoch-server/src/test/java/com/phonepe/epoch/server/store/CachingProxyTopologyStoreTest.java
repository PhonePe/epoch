package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.managed.LeadershipEnsurer;
import com.phonepe.epoch.server.utils.ZkUtils;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyTopologyStoreTest extends TestBase {

    @Test
    @SneakyThrows
    void testCRUD() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val cf = ZkUtils.buildCurator(
                    new ZkConfig().setConnectionString(cluster.getConnectString()))) {
                cf.start();
                val le = mock(LeadershipEnsurer.class);
                val s = new ConsumingFireForgetSignal<Boolean>();
                when(le.onLeadershipChange()).thenReturn(s);
                val ts = new CachingProxyTopologyStore(new ZkTopologyStore(cf, MAPPER), le);

                { //Test CRUD
                    val topo = new EpochTopology("test-topo",
                                                 new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                                .<EpochTask>mapToObj(TestUtils::genContainerTask)
                                                                                .toList(),
                                                                        EpochCompositeTask.CompositionType.ALL),
                                                 new EpochTaskTriggerCron("0/2 * * ? * * *"));
                    val topologyId = topologyId(topo);
                    assertEquals(topo, ts.save(topo).map(EpochTopologyDetails::getTopology).orElse(null));
                    assertEquals(EpochTopologyState.ACTIVE,
                                 ts.save(topo).map(EpochTopologyDetails::getState).orElse(null));
                    assertEquals(EpochTopologyState.PAUSED, ts.updateState(topologyId, EpochTopologyState.PAUSED)
                            .map(EpochTopologyDetails::getState)
                            .orElse(null));
                    assertNull(ts.updateState("Wrong", EpochTopologyState.PAUSED)
                                       .map(EpochTopologyDetails::getState)
                                       .orElse(null));
                    assertTrue(ts.delete(topologyId));
                    assertNull(ts.get(topologyId).orElse(null));
                }
                { //Test List
                    IntStream.rangeClosed(1, 100)
                            .forEach(i -> ts.save(new EpochTopology("test-topo-" + i,
                                                                    new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                                                   .<EpochTask>mapToObj(
                                                                                                           TestUtils::genContainerTask)
                                                                                                   .toList(),
                                                                                           EpochCompositeTask.CompositionType.ALL),
                                                                    new EpochTaskTriggerCron("0/2 * * ? * * *"))));
                    assertEquals(100, ts.list(x -> true).size());
                    assertEquals(50,
                                 ts.list(x -> Integer.parseInt(x.getTopology().getName().split("\\-")[2]) % 2 == 0)
                                         .size());
                    s.dispatch(true);
                    assertEquals(100, ts.list(x -> true).size());
                }
            }
        }
    }
}