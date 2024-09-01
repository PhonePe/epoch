package com.phonepe.epoch.server;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.phonepe.epoch.server.config.EpochOptionsConfig;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.execution.TopologyExecutorImpl;
import com.phonepe.epoch.server.managed.LeadershipManager;
import com.phonepe.epoch.server.mocks.MockTaskExecutionEngine;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.CachingProxyTopologyRunInfoStore;
import com.phonepe.epoch.server.store.CachingProxyTopologyStore;
import com.phonepe.epoch.server.store.InMemoryTopologyRunInfoStore;
import com.phonepe.epoch.server.store.InMemoryTopologyStore;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import io.dropwizard.lifecycle.setup.ExecutorServiceBuilder;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

import static org.mockito.Mockito.mock;

public class TestModule extends AbstractModule {
    private final Environment environment;
    private final ObjectMapper mapper;
    private final MockTaskExecutionEngine taskExecutionEngine;


    public TestModule(Environment environment, final ObjectMapper mapper, MockTaskExecutionEngine taskExecutionEngine) {
        this.environment = environment;
        this.mapper = mapper;
        this.taskExecutionEngine = taskExecutionEngine;
    }

    @Override
    public void configure() {
        bind(CuratorFramework.class).toInstance(mock(CuratorFramework.class));
        bind(TaskExecutionEngine.class).toInstance(taskExecutionEngine);
        bind(TopologyExecutor.class).to(TopologyExecutorImpl.class);

        bind(TopologyStore.class).to(CachingProxyTopologyStore.class);
        bind(TopologyStore.class).annotatedWith(Names.named("rootTopologyStore")).toInstance(new InMemoryTopologyStore());

        bind(TopologyRunInfoStore.class).to(CachingProxyTopologyRunInfoStore.class);
        bind(TopologyRunInfoStore.class).annotatedWith(Names.named("rootRunInfoStore")).toInstance(new InMemoryTopologyRunInfoStore());
    }

    @Provides
    @Singleton
    public MetricRegistry metricRegistry() {
        return SharedMetricRegistries.getOrCreate("test");
    }

    @Provides
    @Singleton
    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    @Provides
    @Singleton
    public Environment getEnvironment() {
        return environment;
    }


    @Provides
    @Singleton
    public EpochOptionsConfig optionsConfig() {
        return new EpochOptionsConfig().setNumRunsPerJob(5).setCleanupJobInterval(Duration.seconds(1));
    }

    @Provides
    @Named("taskPool")
    @Singleton
    public ExecutorService taskPool(MetricRegistry metricRegistry) {
        return new ExecutorServiceBuilder(new LifecycleEnvironment(metricRegistry), "task-pool-%d")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(10)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
    }

    @Provides
    @Singleton
    public LeadershipManager leadershipManager() {
        return TestUtils.createLeadershipManager(true);
    }

}
