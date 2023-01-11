package com.phonepe.epoch.server.healthchecks;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.NamedHealthCheck;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class ZkConnectionCheck extends NamedHealthCheck {

    private final CuratorFramework curatorFramework;

    @Inject
    public ZkConnectionCheck(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
    }

    @Override
    protected Result check() throws Exception {
        return curatorFramework.getState() == CuratorFrameworkState.STARTED
               && curatorFramework.checkExists().forPath("/") != null
                ? Result.healthy()
               : Result.unhealthy("ZK connection is not in healthy state");
    }

    @Override
    public String getName() {
        return "zk-connection";
    }
}
