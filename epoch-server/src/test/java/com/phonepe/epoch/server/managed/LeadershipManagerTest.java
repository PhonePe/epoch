package com.phonepe.epoch.server.managed;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.epoch.server.utils.EpochUtils.hostname;
import static com.phonepe.epoch.server.utils.ZkUtils.buildCurator;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class LeadershipManagerTest extends TestBase {

    @Test
    @SneakyThrows
    void testLeadership() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.blockUntilConnected();
                val env = mock(Environment.class);
                val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
                when(env.lifecycle()).thenReturn(lifecycle);

                val l1 = new LeadershipManager(curator, env);
                val l2 = new LeadershipManager(curator, env);

                val leaderUpdated1 = new AtomicBoolean();
                l1.onLeadershipStateChange().connect(value -> leaderUpdated1.set(true));
                val leaderUpdated2 = new AtomicBoolean();
                l2.onLeadershipStateChange().connect(value -> leaderUpdated2.set(true));
                assertNull(l1.leader().orElse(null));
                l1.start();
                l1.serverStarted(server(8080));
                val host = hostname();
                //l2.start();
                await()
                        .atMost(Duration.ofMinutes(1))
                        .until(leaderUpdated1::get);
                assertTrue(leaderUpdated1.get());
                assertTrue(l1.isLeader());
                assertEquals("http://" + host + ":8080", l1.leader().orElse(null));
                //Check failover

                l2.start();
                l2.serverStarted(server(9000));
                assertFalse(l2.isLeader());
                assertNull(l2.leader().orElse(null));
                l1.stop();
                await()
                        .atMost(Duration.ofMinutes(1))
                        .until(leaderUpdated2::get);
                assertTrue(leaderUpdated2.get());
                assertTrue(l2.isLeader());
                assertEquals("http://" + host + ":9000", l2.leader().orElse(null));
            }
        }
    }

    private Server server(int port) {
        val server = mock(Server.class);
        val conn = mock(ServerConnector.class);
        when(conn.getLocalPort()).thenReturn(port);
        when(server.getConnectors()).thenReturn(new ServerConnector[] { conn });
        return server;
    }
}