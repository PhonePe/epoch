package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.config.EpochOptionsConfig;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
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
import java.util.Collection;
import java.util.Objects;

/**
 *
 */
@Slf4j
@Singleton
@Order(50)
public class CleanupTask implements Managed {

    public static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.minutes(5);
    public static final int DEFAULT_NUM_RUNS_PER_JOB = 5;

    public static final String CLEANUP_HANDLER_NAME = "CLEANUP_HANDLER";

    private final LeadershipManager leadershipManager;
    private final ScheduledSignal cleanupJobRunner;
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore topologyRunInfoStore;
    private final TaskExecutionEngine taskEngine;

    private final int maxRuns;


    @Inject
    public CleanupTask(
            final EpochOptionsConfig options,
            LeadershipManager leadershipManager,
            TopologyStore topologyStore,
            TopologyRunInfoStore topologyRunInfoStore,
            TaskExecutionEngine taskEngine) {
        this.leadershipManager = leadershipManager;
        this.topologyStore = topologyStore;
        this.topologyRunInfoStore = topologyRunInfoStore;
        this.maxRuns = options.getNumRunsPerJob() == 0 ? DEFAULT_NUM_RUNS_PER_JOB : options.getNumRunsPerJob();
        this.taskEngine = taskEngine;
        val jobInterval = Objects.requireNonNullElse(options.getCleanupJobInterval(), DEFAULT_CLEANUP_INTERVAL)
                .toMilliseconds();
        this.cleanupJobRunner = new ScheduledSignal(java.time.Duration.ofMillis(jobInterval));
    }

    @Override
    public void start() throws Exception {
        cleanupJobRunner.connect(CLEANUP_HANDLER_NAME, time -> {
            log.info("Remote task cleanup job activated at: {}", time);
            if (!leadershipManager.isLeader()) {
                log.debug("NOOP for cleanup as i'm not the leader");
                return;
            }
            topologyStore.list(topology -> !topology.getState().equals(EpochTopologyState.DELETED))
                    .stream()
                    .map(EpochTopologyDetails::getId)
                    .forEach(topologyId -> {
                        val runs = topologyRunInfoStore.list(
                                        topologyId, run -> !run.getState().equals(EpochTopologyRunState.RUNNING));
                        if (runs.size() < maxRuns) {
                            log.debug("Nothing needs to be done as the known runs for topology {} is {} which is less than threshold {}",
                                      topologyId, runs.size(), maxRuns);
                            return;
                        }
                        cleanupRuns(topologyId, runs);
                    });
            log.info("Remote task cleanup job activated at {} is now complete", time);

        });
    }


    @Override
    public void stop() throws Exception {
        cleanupJobRunner.disconnect(CLEANUP_HANDLER_NAME);
        cleanupJobRunner.close();
    }

    private void cleanupRuns(String topologyId, Collection<EpochTopologyRunInfo> runs) {
        try {
        runs.stream()
                .skip(maxRuns)
                .forEach(run -> {
                    val runId = run.getRunId();
                    val status = topologyRunInfoStore.delete(topologyId, runId);
                    log.info("Deletion status for {}/{}: {}", topologyId, runId, status);
                    if(status) {
                        if (taskEngine.cleanup(run)) {
                            log.debug("Task clean up complete for {}/{}", topologyId, runId);
                        }
                        else {
                            log.warn("Task cleanup failed for {}/{}", topologyId, runId);
                        }
                    }
                });
        } catch (Exception e) {
            log.info("Error deleting topology runs for topology " + topologyId + ": " + e.getMessage(), e);
        }
    }

}
