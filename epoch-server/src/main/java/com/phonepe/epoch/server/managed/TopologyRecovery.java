package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.epoch.server.utils.EpochUtils.updateTopologySchedule;

/**
 *
 */
@Order(30)
@Singleton
@Slf4j
public class TopologyRecovery implements Managed {
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;
    private final Scheduler scheduler;

    @Inject
    public TopologyRecovery(
            LeadershipManager leadershipManager,
            TopologyStore topologyStore,
            TopologyRunInfoStore runInfoStore,
            Scheduler scheduler) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.scheduler = scheduler;

        leadershipManager.onLeadershipStateChange().connect(isLeader -> {
            if(Boolean.TRUE.equals(isLeader)) {
                log.info("This node became leader. Will recover topologies");
                scheduler.clear();
                recoverTopologyRuns();
            }
            else {
                log.info("Purging scheduler queue");
                scheduler.clear();
            }
        });
    }

    @Override
    public void start() throws Exception {
        //Nothing to do here
    }

    @Override
    public void stop() throws Exception {
        //Nothing to do here
    }

    private void recoverTopologyRuns() {
        val topologies = topologyStore.list(t -> !t.getState().equals(EpochTopologyState.DELETED))
                .stream()
                .collect(Collectors.toMap(EpochTopologyDetails::getId, Function.identity()));
        log.info("Recovering topologies: {}", topologies.keySet());
        topologies.forEach((tId, topologyDetails) -> {
            val activeRuns = runInfoStore.list(tId, run -> run.getState().equals(EpochTopologyRunState.RUNNING));

            activeRuns.forEach(run -> {
                val scheduleId = EpochUtils.scheduleId(topologyDetails);
                try {
                    val status = scheduler.recover(
                            topologyDetails.getId(),
                            scheduleId,
                            run.getRunId(),
                            new Date(),
                            run.getRunType());
                    if (status) {
                        log.info("Recovered {} topology run {}/{}/{}",
                                 run.getRunType().name().toLowerCase(),
                                 topologyDetails.getId(),
                                 scheduleId,
                                 run.getRunId());
                    }
                    else {
                        log.info("Failed to recover {} topology run {}/{}",
                                 run.getRunType().name().toLowerCase(),
                                 topologyDetails.getId(),
                                 run.getRunId());
                    }
                }
                catch (Exception e) {
                    log.error("Could not recover topology run {}/{}/{}. Error: {}",
                            topologyDetails.getId(), scheduleId, run.getRunId(), EpochUtils.errorMessage(e), e);
                }
            });
            try {
                updateTopologySchedule(topologyDetails, scheduler);
            }
            catch (Exception e) {
                log.error("Could not reschedule topology " + topologyDetails.getId() + ". Error: " + EpochUtils.errorMessage(e), e);
            }
        });
    }
}
