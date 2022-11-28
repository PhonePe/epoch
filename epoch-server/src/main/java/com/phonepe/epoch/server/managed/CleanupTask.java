package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.config.EpochOptionsConfig;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.Objects;

/**
 *
 */
@Slf4j
@Singleton
@Order(50)
public class CleanupTask implements Managed {

    private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.seconds(30);
    private static final int DEFAULT_NUM_RUNS_PER_JOB = 5;

    private final LeadershipManager leadershipManager;
    private final ScheduledSignal cleanupJobRunner;
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore topologyRunInfoStore;

    private final int maxRuns;


    @Inject
    public CleanupTask(
            final EpochOptionsConfig options, LeadershipManager leadershipManager,
            TopologyStore topologyStore,
            TopologyRunInfoStore topologyRunInfoStore) {
        this.leadershipManager = leadershipManager;
        this.topologyStore = topologyStore;
        this.topologyRunInfoStore = topologyRunInfoStore;
        this.maxRuns = options.getNumRunsPerJob() == 0 ? DEFAULT_NUM_RUNS_PER_JOB : options.getNumRunsPerJob();
        val jobInterval = Objects.requireNonNullElse(options.getCleanupJobInterval(), DEFAULT_CLEANUP_INTERVAL)
                .toMilliseconds();
        this.cleanupJobRunner = new ScheduledSignal(java.time.Duration.ofMillis(jobInterval));
    }

    @Override
    public void start() throws Exception {
        cleanupJobRunner.connect("CLEANUP_HANDLER", time -> {
            if (!leadershipManager.isLeader()) {
                log.info("NOOP for cleanup as i'm not the leader");
                return;
            }
            topologyStore.list(topology -> !topology.getState().equals(EpochTopologyState.DELETED))
                    .stream()
                    .map(EpochTopologyDetails::getId)
                    .forEach(topologyId -> {
                        val runs = topologyRunInfoStore.list(
                                        topologyId, run -> !run.getState().equals(EpochTopologyRunState.RUNNING))
                                .stream()
                                .sorted(Comparator.comparing(EpochTopologyRunInfo::getUpdated).reversed())
                                .toList();
                        if (runs.size() < maxRuns) {
                            log.debug("Nothing needs to be done as the known runs for topology {} is {} which is less than threshold {}",
                                      topologyId, runs.size(), maxRuns);
                            return;
                        }
                        try {
                        runs.stream()
                                .skip(maxRuns)
                                .forEach(run -> log.info("Deletion status for {}/{}: {}",
                                                         run.getTopologyId(),
                                                         run.getRunId(),
                                                         topologyRunInfoStore.delete(run.getTopologyId(), run.getRunId())));
                        } catch (Exception e) {
                            log.info("Error deleting topology runs for topology " + topologyId + ": " + e.getMessage(), e);
                        }
                    });
        });
    }

    @Override
    public void stop() throws Exception {

    }
}
