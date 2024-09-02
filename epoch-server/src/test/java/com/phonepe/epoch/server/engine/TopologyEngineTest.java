package com.phonepe.epoch.server.engine;

import com.google.inject.Module;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.E2ETestBase;
import com.phonepe.epoch.server.TestUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.phonepe.epoch.server.TestUtils.ensureUntil;
import static com.phonepe.epoch.server.TestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyEngineTest extends E2ETestBase {

    private static final MailNotificationSpec DUMMY_NOTIFICATION_SPEC = new MailNotificationSpec(List.of("test@email.com"));
    private static final EpochTaskTriggerCron TRIGGER_EVERY_2S = new EpochTaskTriggerCron("0/2 * * * * ?");
    private static final EpochTaskTriggerCron TRIGGER_EVERY_5S = new EpochTaskTriggerCron("0/5 * * * * ?");
    private static final EpochTaskTriggerCron TRIGGER_EVERY_10S = new EpochTaskTriggerCron("0/10 * * * * ?");
    private static final Duration DURATION_20S = Duration.ofSeconds(20);

    @Inject
    private TopologyEngine topologyEngine;

    private final Random random = new SecureRandom();

    @Test
    public void testCreationOfTopologyWithSuccessfulExecution() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(TestUtils.generateTopologyDesc(newRandomInt(), TRIGGER_EVERY_5S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* wait for 2 runs */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 2, DURATION_20S);
    }

    @Test
    public void testCreationAndPauseUnPauseOfTopologyWithSuccessfulExecution() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(TestUtils.generateTopologyDesc(newRandomInt(), TRIGGER_EVERY_5S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* wait for 1 runs */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 1, DURATION_20S);

        /* pause the topology */
        topologyEngine.updateState(topologyId, EpochTopologyState.PAUSED);

        /* after the pause, there should be 0 or 1 more run, but not more than that */
        ensureUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 2
                && taskExecutionEngine.capturedTasksSize(topologyId) > 0, 10);

        /* unpause the topology */
        topologyEngine.updateState(topologyId, EpochTopologyState.ACTIVE);

        /* after unpause, 2 more runs should definitely happen in the next 20s */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 3, DURATION_20S);
    }

    @Test
    public void testCreationAndDeletionOfTopologyWithSuccessfulExecution() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(TestUtils.generateTopologyDesc(newRandomInt(), TRIGGER_EVERY_5S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* wait for 1 runs */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 1, DURATION_20S);

        topologyEngine.delete(topologyId);

        /* after the pause, there should be 0 or 1 more run, but not more than that */
        ensureUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 2
                && taskExecutionEngine.capturedTasksSize(topologyId) > 0, 10);
    }

    @Test
    public void testUpdateOfTopologyWithSuccessfulExecution() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(TestUtils.generateTopologyDesc(newRandomInt(), TRIGGER_EVERY_5S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* no runs should happen at the start, even after 10 seconds */
        ensureUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 2
                && taskExecutionEngine.capturedTasksSize(topologyId) > 0, 10);

        /* update the topology to run every 2 seconds */
        final var updatedTopology = TestUtils.updateTopologyCronSpec(save.get().getTopology(), TRIGGER_EVERY_2S);
        topologyEngine.update(topologyId, updatedTopology);

        /* minimum 2 runs should happen in the next 20 seconds */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 3
                && taskExecutionEngine.capturedTasksSize(topologyId) > 1, DURATION_20S);
    }

    @Test
    public void testUpdateOfTopologyThatIsAlreadyRunning() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(
                TestUtils.generateTopologyDesc(newRandomInt(), TRIGGER_EVERY_2S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* wait for 1 runs */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 1, DURATION_20S);

        /* update the topology to run every 10 seconds */
        final var updatedTopology = TestUtils.updateTopologyCronSpec(save.get().getTopology(), TRIGGER_EVERY_10S);
        topologyEngine.update(topologyId, updatedTopology);

        /* maximum 2 more runs should happen in the next 15 seconds */
        ensureUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 3
                && taskExecutionEngine.capturedTasksSize(topologyId) > 0, 15);
    }

    @Test
    public void testUpdateOfTopologyDuringPause() {
        assert taskExecutionEngine.capturedTasksSize() == 0;

        // create a topology
        final var save = topologyEngine.save(
                TestUtils.generateTopologyDesc(1, TRIGGER_EVERY_2S, DUMMY_NOTIFICATION_SPEC));
        assertTrue(save.isPresent());
        final var topologyId = save.get().getId();

        /* wait for 1 runs */
        waitUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) == 1, DURATION_20S);

        /* pause the topology */
        topologyEngine.updateState(topologyId, EpochTopologyState.PAUSED);

        /* update the topology to run every 10 seconds */
        final var updatedTopology = TestUtils.updateTopologyCronSpec(save.get().getTopology(),
                TRIGGER_EVERY_10S);
        topologyEngine.update(topologyId, updatedTopology);

        /* unpause the topology */
        topologyEngine.updateState(topologyId, EpochTopologyState.ACTIVE);

        /* maximum 2 more runs should happen in the next 15 seconds */
        ensureUntil(() -> taskExecutionEngine.capturedTasksSize(topologyId) <= 3
                && taskExecutionEngine.capturedTasksSize(topologyId) > 0, 15);
    }

    @Override
    public Stream<Module> customModule() {
        return Stream.empty();
    }

    private int newRandomInt() {
        return random.nextInt(100000);
    }
}