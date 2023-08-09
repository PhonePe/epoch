package com.phonepe.epoch.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.phonepe.epoch.server.config.*;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.execution.TopologyExecutorImpl;
import com.phonepe.epoch.server.managed.CleanupTask;
import com.phonepe.epoch.server.notify.EventMailDataConverter;
import com.phonepe.epoch.server.notify.NotificationBlackholeSender;
import com.phonepe.epoch.server.notify.NotificationMailSender;
import com.phonepe.epoch.server.notify.NotificationSender;
import com.phonepe.epoch.server.remote.DroveTaskExecutionEngine;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.*;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import com.phonepe.epoch.server.utils.ZkUtils;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

/**
 *
 */
@IgnoreInJacocoGeneratedReport(reason = "guava module .. nothing to test")
@Slf4j
public class EpochModule extends AbstractModule {


    @Override
    protected void configure() {
        bind(TaskExecutionEngine.class).to(DroveTaskExecutionEngine.class);
        bind(TopologyExecutor.class).to(TopologyExecutorImpl.class);

        bind(TopologyStore.class).to(CachingProxyTopologyStore.class);
        bind(TopologyStore.class).annotatedWith(Names.named("rootTopologyStore")).to(ZkTopologyStore.class);

        bind(TopologyRunInfoStore.class).to(CachingProxyTopologyRunInfoStore.class);
        bind(TopologyRunInfoStore.class)
                .annotatedWith(Names.named("rootRunInfoStore"))
                .to(ZkTopologyRunInfoStore.class);
    }

    @Provides
    @Singleton
    public ObjectMapper mapper(final Environment environment) {
        return environment.getObjectMapper();
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
    @Singleton
    public EpochOptionsConfig optionsConfig(final AppConfig appConfig) {
        val options = Objects.requireNonNullElse(appConfig.getOptions(),
                                          new EpochOptionsConfig()
                                                  .setCleanupJobInterval(CleanupTask.DEFAULT_CLEANUP_INTERVAL)
                                                  .setNumRunsPerJob(CleanupTask.DEFAULT_NUM_RUNS_PER_JOB));
        log.info("Epoch options: {}", options);
        return options;
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

    @Provides
    @Singleton
    public NotificationSender notificationConfig(
            final TopologyStore topologyStore,
            final TopologyRunInfoStore runInfoStore,
            final AppConfig appConfig) {
        return Objects.requireNonNullElse(appConfig.getNotify(), BlackholeNotificationConfig.INSTANCE)
                .accept(new NotificationConfigVisitor<NotificationSender>() {
                    @Override
                    public NotificationSender visit(MailNotificationConfig mailConfig) {
                        val converter = new EventMailDataConverter(topologyStore,
                                                                   runInfoStore,
                                                                   Objects.requireNonNullElse(mailConfig.getDefaultEmails(),
                                                                                              List.of()));
                        return new NotificationMailSender(mailConfig, converter);
                    }

                    @Override
                    public NotificationSender visit(BlackholeNotificationConfig blackholeConfig) {
                        return new NotificationBlackholeSender();
                    }
                });
    }
}
