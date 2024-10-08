package com.phonepe.epoch.server.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
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
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
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
@SuppressWarnings("java:S1075")
public class LeadershipManager implements LeaderSelectorListener, Managed, ServerLifecycleListener {
    private static final String LEADER_SELECTION_MTX_PATH = "/leader-selection";

    private final ConsumingFireForgetSignal<Boolean> leadershipStateChanged = new ConsumingFireForgetSignal<>();

    private final LeaderSelector selector;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean leader = new AtomicBoolean();
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
        } finally {
            stopLock.unlock();
        }
        this.selector.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    public ConsumingFireForgetSignal<Boolean> onLeadershipStateChange() {
        return leadershipStateChanged;
    }

    public boolean isLeader() {
        return started.get() && leader.get();
    }

    @SneakyThrows
    public Optional<String> leader() {
        return started.get()
               ? leaderOptional()
               : Optional.empty();
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) {
        log.info("This node became leader. Notifying everyone");
        leadershipStateChanged.dispatch(true);
        stopLock.lock();
        leader.set(true);
        try {
            while (!stopped.get()) {
                stopCondition.await();
            }
            log.info("Stop called, leadership relinquished");
        }
        catch (InterruptedException e) {
            log.info("Lost leadership");
            leadershipStateChanged.dispatch(false);
            Thread.currentThread().interrupt();
        }
        finally {
            leader.set(false);
            stopLock.unlock();
        }
    }

    @Override
    public void serverStarted(Server server) {
        log.info("Dropwizard server started. Starting leader election.");
        val connector = (ServerConnector) server.getConnectors()[0];
        val specifiedPort = connector.getLocalPort();
        val port = determinePort(specifiedPort);
        val isHttp = connector.getConnectionFactory("ssl") == null;
        val host = EpochUtils.hostname();
        val id = String.format("%s://%s:%d", isHttp ? "http" : "https", host, port);
        log.info("Node id being set to {}", id);
        selector.setId(id);
        this.selector.start();
        this.started.set(true);
    }

    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        if (client.getConnectionStateErrorPolicy().isErrorState(newState) && started.get()) {
            log.error("ZK connection state went to {}. Will commit seppuku.", newState);
            System.exit(-1);
        }
    }

    @VisibleForTesting
    static int determinePort(int specifiedPort) {
        //If the app is deployed on drove, this would be mapped to a random port.
        //We need to use that in the id. If no such mapping exists, the original port is used.
        val mappedPort = System.getenv("PORT_" + specifiedPort);
        return Strings.isNullOrEmpty(mappedPort) ? specifiedPort : Integer.parseInt(mappedPort);
    }

    /**
     * From {@link LeaderSelector} If for some reason there is no current leader, a dummy
     * {@link org.apache.curator.framework.recipes.leader.Participant} is returned.
     * We are handling that case in thisfunction
     *
     * @return leader id if present
     * @throws Exception if curator throws an exception
     */
    private Optional<String> leaderOptional() throws Exception {
        final String id = selector.getLeader().getId();
        if (Strings.isNullOrEmpty(id)) {
            return Optional.empty();
        }
        return Optional.of(id);
    }
}
