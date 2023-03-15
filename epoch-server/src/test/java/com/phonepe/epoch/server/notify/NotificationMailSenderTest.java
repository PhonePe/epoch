package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyState;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.config.MailNotificationConfig;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class NotificationMailSenderTest {

    @Test
    void testSendMail() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of());
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.COMPLETED, EpochTaskRunState.COMPLETED);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    val email = invocationOnMock.getArgument(0, Email.class);
                    ctr.set(Objects.equals(email.getSubject(), "Topology run TEST_TOPO-1/TR-1 completed successfully"));
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertTrue(ctr.get());
    }

    @Test
    void testSendMailForFailure() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of());
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.FAILED, EpochTaskRunState.FAILED);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    val email = invocationOnMock.getArgument(0, Email.class);
                    ctr.set(Objects.equals(email.getSubject(), "Topology run TEST_TOPO-1/TR-1 failed"));
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertTrue(ctr.get());
    }

    @Test
    void testSendMailToDefault() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of("test@email.com"));
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1, BlackholeNotificationSpec.DEFAULT);
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.COMPLETED, EpochTaskRunState.COMPLETED);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    ctr.set(true);
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertTrue(ctr.get());
    }

    @Test
    void testIgnoreEventTypeMismatch() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of());
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.FAILED, EpochTaskRunState.FAILED);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    ctr.set(true); //This should not get called
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_TASK_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertFalse(ctr.get());
    }

    @Test
    void testIgnoreMailNoEmail() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of());
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1);
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.COMPLETED, EpochTaskRunState.COMPLETED);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    ctr.set(true); //Should not get called
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertFalse(ctr.get());
    }

    @Test
    void testIgnoreMailIgnoredState() {
        val ts = mock(TopologyStore.class);
        val trs = mock(TopologyRunInfoStore.class);
        val mc = new MailNotificationConfig("localhost", 2525, false, "", "", List.of());
        val mailer = mock(Mailer.class);
        val ctr = new AtomicBoolean();
        val ns = new NotificationMailSender(ts, trs, mc, mailer);
        val spec = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        val topologyId = topologyId(spec);
        val topology = new EpochTopologyDetails(topologyId,
                                                spec,
                                                EpochTopologyState.ACTIVE,
                                                new Date(),
                                                new Date());
        val run = TestUtils.genRunInfo(topologyId, 1, EpochTopologyRunState.RUNNING, EpochTaskRunState.RUNNING);
        when(ts.get(topologyId)).thenReturn(Optional.of(topology));
        when(trs.get(topologyId, run.getRunId())).thenReturn(Optional.of(run));
        when(mailer.sendMail(any(Email.class)))
                .thenAnswer(invocationOnMock -> {
                    ctr.set(true); //Should not get called
                    return new CompletableFuture<>();
                });
        ns.consume(EpochStateChangeEvent.builder()
                           .type(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)
                           .metadata(Map.of(StateChangeEventDataTag.TOPOLOGY_ID, run.getTopologyId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_ID, run.getRunId(),
                                            StateChangeEventDataTag.TOPOLOGY_RUN_TYPE, run.getRunType(),
                                            StateChangeEventDataTag.NEW_STATE, run.getState()))
                           .build());
        assertFalse(ctr.get());
    }
}