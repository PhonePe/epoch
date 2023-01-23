package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.utils.EpochUtils;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Order(10)
@Singleton
public class LeadershipManager extends LeaderSelectorListenerAdapter implements Managed, ServerLifecycleListener {
    private static final String LEADER_SELECTION_MTX_PATH = "/leader-selection";

    private final ConsumingFireForgetSignal<Void> gainedLeadership = new ConsumingFireForgetSignal<>();

    private final LeaderSelector selector;

    private AtomicBoolean started = new AtomicBoolean();
    private AtomicBoolean leader = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Lock stopLock = new ReentrantLock();
    private final Condition stopCondition = stopLock.newCondition();

    @Inject
    public LeadershipManager(CuratorFramework curatorFramework,
                             Environment environment) {
        this.selector = new LeaderSelector(curatorFramework, LEADER_SELECTION_MTX_PATH, this);
        this.selector.autoRequeue();
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        //Nothing to do here
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        stopLock.lock();
        try {
            stopped.set(true);
            stopCondition.signalAll();
        }
        finally {
            stopLock.unlock();
        }
        this.selector.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    public ConsumingFireForgetSignal<Void> onGainingLeadership() {
        return gainedLeadership;
    }

    public boolean isLeader() {
        return started.get() && leader.get();
    }

    @SneakyThrows
    public Optional<String> leader() {
        return started.get() && leader.get()
               ? Optional.of(selector.getLeader().getId())
               : Optional.empty();
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
        log.info("This node became leader. Notifying everyone");
        gainedLeadership.dispatch(null);
        stopLock.lock();
        leader.set(true);
        try {
            while (!stopped.get()) {
                stopCondition.await();
            }
            log.info("Stop called, leadership relinquished");
        }
        finally {
            leader.set(false);
            stopLock.unlock();
        }
    }

    @Override
    public void serverStarted(Server server) {
        log.info("Dropwizard server started. Starting leader election.");
        val port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        val isHttp = server.getConnectors()[0].getConnectionFactory("ssl") == null;
        val host = EpochUtils.hostname();
        val id = String.format("%s://%s:%d", isHttp ? "http" : "https", host, port);
        log.info("Node id being set to {}", id);
        selector.setId(id);
        this.selector.start();
        this.started.set(true);
    }
}
