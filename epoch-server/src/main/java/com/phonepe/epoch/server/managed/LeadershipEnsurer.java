package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.store.TopologyStore;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@Order(10)
@Singleton
public class LeadershipEnsurer implements Managed {
    private final LeaderLatch leaderLatch;

    private final TopologyStore topologyStore;
    private final Scheduler scheduler;

    private final ConsumingFireForgetSignal<Boolean> leadershipChanged = new ConsumingFireForgetSignal<>();
    private final AtomicBoolean wasLeader = new AtomicBoolean();

    @SuppressWarnings("java:S1075")
    @Inject
    public LeadershipEnsurer(
            CuratorFramework curatorFramework,
            TopologyStore topologyStore,
            Scheduler scheduler) {
        this.topologyStore = topologyStore;
        this.scheduler = scheduler;
        this.leaderLatch = new LeaderLatch(curatorFramework, "/leadership");
        this.leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("This node became leader. Will recover topologies.");
                wasLeader.set(true);
                leadershipChanged.dispatch(true);
            }

            @Override
            public void notLeader() {
                if(wasLeader.get()) {
                    log.info("This node lost leadership. What is the point in living .. Committing seppuku ... :'( ");
                    leadershipChanged.dispatch(true);
                    System.exit(1);
                }
                else {
                    log.info("This node is not the leader");
                }
            }
        });
    }

    public ConsumingFireForgetSignal<Boolean> onLeadershipChange() {
        return leadershipChanged;
    }

    @Override
    public void start() throws Exception {
        leaderLatch.start();
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        leaderLatch.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    public boolean isLeader() {
        return this.leaderLatch.hasLeadership();
    }



}
