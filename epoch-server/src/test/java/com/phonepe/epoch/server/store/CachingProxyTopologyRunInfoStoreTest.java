package com.phonepe.epoch.server.store;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.managed.LeadershipManager;
import com.phonepe.epoch.server.utils.ZkUtils;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyTopologyRunInfoStoreTest extends TestBase {
    @Test
    @SneakyThrows
    void testCRUD() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = ZkUtils.buildCurator(
                    new ZkConfig().setConnectionString(cluster.getConnectString()))) {
                val lm = mock(LeadershipManager.class);
                val s = new ConsumingFireForgetSignal<Void>();
                when(lm.onGainingLeadership()).thenReturn(s);
                val ris = new CachingProxyTopologyRunInfoStore(new ZkTopologyRunInfoStore(curator, MAPPER), lm);
                {
                    val executionInfo = new EpochTopologyRunInfo("TID1",
                                                                 "RID1",
                                                                 EpochTopologyRunState.RUNNING,
                                                                 "",
                                                                 Map.of("TT_1", new EpochTopologyRunTaskInfo().setUpstreamId("").setState(EpochTaskRunState.RUNNING)),
                                                                 EpochTopologyRunType.SCHEDULED,
                                                                 new Date(),
                                                                 new Date());
                    assertEquals(executionInfo, ris.save(executionInfo).orElse(null));
                    val topologyId = executionInfo.getTopologyId();
                    val runId = executionInfo.getRunId();
                    assertEquals(EpochTaskRunState.COMPLETED,
                                 ris.updateTaskState(topologyId, runId, "TT_1", EpochTaskRunState.COMPLETED)
                                         .map(d -> d.getTasks().get("TT_1"))
                                         .orElse(null));
                    assertTrue(ris.delete(topologyId, runId));
                    assertNull(ris.get(topologyId, runId).orElse(null));
                }
                {
                    IntStream.rangeClosed(1, 100)
                            .forEach(i -> IntStream.rangeClosed(1, 25)
                                    .forEach(j -> ris.save(new EpochTopologyRunInfo("TID-" + i,
                                                                                    "RID-" + j,
                                                                                    EpochTopologyRunState.RUNNING,
                                                                                    "",
                                                                                    Map.of("TT_1", new EpochTopologyRunTaskInfo().setUpstreamId("").setState(EpochTaskRunState.RUNNING)),
                                                                                    EpochTopologyRunType.SCHEDULED,
                                                                                    new Date(),
                                                                                    new Date()))));
                    IntStream.rangeClosed(1, 100)
                            .forEach(i -> {
                                assertEquals(25, ris.list("TID-" + i, x -> true).size());
                                assertTrue(ris.deleteAll("TID-" + i));
                                assertTrue(ris.list("TID-" + i, x -> true).isEmpty());
                            });
                }
            }
        }
    }
}