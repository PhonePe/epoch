package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;

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

        leadershipManager.onGainingLeadership().connect(data -> {
            log.info("This node became leader. Will recover topologies");
            recoverTopologyRuns();
        });
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }

    private void recoverTopologyRuns() {
        val topologies = topologyStore.list(t -> !t.getState().equals(EpochTopologyState.DELETED))
                .stream()
                .collect(Collectors.toMap(EpochTopologyDetails::getId, Function.identity()));
        log.info("Recovering topologies: {}", topologies.keySet());
        topologies.forEach((tId, t) -> {
            val activeRuns = runInfoStore.list(tId, run -> run.getState().equals(EpochTopologyRunState.RUNNING));

            activeRuns.forEach(run -> {
                val status = scheduler.recover(
                        t.getId(),
                        run.getRunId(),
                        new Date(),
                        0);
                if (status) {
                    log.info("Scheduled topology {} for execution", t.getId());
                }
                else {
                    log.warn("Could not schedule topology {} for execution", t.getId());
                }
            });
/*
            val lastRunStartTime = activeRuns.stream()
                    .findFirst()
                    .map(EpochTopologyRunInfo::getCreated)
                    .orElse(new Date());
            scheduleTopology(t, scheduler, lastRunStartTime);
*/
            scheduleTopology(t, scheduler, new Date());
        });
    }
}
