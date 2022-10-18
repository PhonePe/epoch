package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.store.TopologyStore;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.epoch.server.utils.EpochUtils.scheduleTopology;

/**
 *
 */
@Slf4j
@Order(50)
public class ExecutionRecovery implements Managed {

    private final TopologyStore topologyStore;
    private final Scheduler scheduler;

    @Inject
    public ExecutionRecovery(TopologyStore topologyStore, Scheduler scheduler) {
        this.topologyStore = topologyStore;
        this.scheduler = scheduler;
    }

    @Override
    public void start() throws Exception {
        val topologies = topologyStore.list(t -> !t.getState().equals(EpochTopologyState.DELETED))
                .stream()
                .collect(Collectors.toMap(EpochTopologyDetails::getId, Function.identity()));
        log.info("Recovering topologies: {}", topologies.keySet());
        topologies.forEach((tId, t) -> {
            scheduleTopology(t, scheduler);
        });
    }

    @Override
    public void stop() throws Exception {

    }
}
