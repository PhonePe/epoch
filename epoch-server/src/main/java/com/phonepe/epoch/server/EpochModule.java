package com.phonepe.epoch.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.phonepe.epoch.server.config.AppConfig;
import com.phonepe.epoch.server.config.DroveConfig;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.execution.TopologyExecutorImpl;
import com.phonepe.epoch.server.remote.DroveTaskExecutionEngine;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.InMemoryTopologyRunInfoStore;
import com.phonepe.epoch.server.store.InMemoryTopologyStore;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.utils.ZkUtils;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

/**
 *
 */
public class EpochModule extends AbstractModule {


    @Override
    protected void configure() {
        bind(TaskExecutionEngine.class).to(DroveTaskExecutionEngine.class);
        bind(TopologyExecutor.class).to(TopologyExecutorImpl.class);
        bind(TopologyStore.class).to(InMemoryTopologyStore.class);
        bind(TopologyRunInfoStore.class).to(InMemoryTopologyRunInfoStore.class);
    }

    @Provides
    @Singleton
    public DroveConfig droveConfig(final AppConfig appConfig) {
        return appConfig.getDrove();
    }

    @Provides
    @Singleton
    public CuratorFramework curator(AppConfig config) {
        return ZkUtils.buildCurator(config.getZookeeper());
    }

    @Provides
    @Named("taskPool")
    @Singleton
    public ExecutorService taskPool(Environment environment) {
        return environment.lifecycle()
                .executorService("task-pool-%d")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
    }
}
