package com.phonepe.epoch.server.healthchecks;

import com.phonepe.epoch.server.utils.ZkUtils;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ZkConnectionCheckTest {

    @Test
    @SneakyThrows
    void testConnectionSuccess() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = ZkUtils.buildCurator(
                    new ZkConfig().setConnectionString(cluster.getConnectString()))) {
                val checker = new ZkConnectionCheck(curator);
                val r = checker.check();
                assertTrue(r.isHealthy());
                assertEquals("zk-connection", checker.getName());
            }
        }
    }

    @Test
    @SneakyThrows
    void testConnectionFailWrongState() {
        val curator = mock(CuratorFramework.class);
        when(curator.getState()).thenReturn(CuratorFrameworkState.STOPPED);
        val checker = new ZkConnectionCheck(curator);
        val r = checker.check();
        assertFalse(r.isHealthy());
    }

    @Test
    @SneakyThrows
    void testConnectionFailNoNode() {
        val curator = mock(CuratorFramework.class);
        val exists = mock(ExistsBuilder.class);
        when(curator.checkExists()).thenReturn(exists);
        when(exists.forPath("/")).thenReturn(null);
        val checker = new ZkConnectionCheck(curator);
        val r = checker.check();
        assertFalse(r.isHealthy());
    }


}