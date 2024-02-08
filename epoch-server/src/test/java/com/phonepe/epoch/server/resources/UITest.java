package com.phonepe.epoch.server.resources;

import com.phonepe.epoch.models.topology.SimpleTopologyEditRequest;
import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.engine.TopologyEngine;
import com.phonepe.epoch.server.error.EpochError;
import com.phonepe.epoch.server.error.EpochErrorCode;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.utils.EpochUtils;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.epoch.server.utils.EpochUtils.detailsFrom;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class UITest extends TestBase {
    private static final TopologyStore topologyStore = mock(TopologyStore.class);
    private static final EpochEventBus epochEventbus = new EpochEventBus();

    private static final Scheduler scheduler = mock(Scheduler.class);

    @AfterEach
    void reset() {
        Mockito.reset(topologyStore);
        Mockito.reset(scheduler);
    }

    @Test
    void testHomeSuccess() {
        val topo = TestUtils.generateTopologyDesc(0);
        val details = EpochUtils.detailsFrom(topo);
        val saveCalled = new AtomicBoolean();
        val scheduleCalled = new AtomicBoolean();
        val updateCalled = new AtomicBoolean();
        when(topologyStore.save(any())).thenAnswer(invocationMock -> {
            saveCalled.set(true);
            return Optional.of(details);
        });
        when(scheduler.schedule(anyString(), anyString(), any(), any())).thenAnswer(invocationOnMock -> {
            scheduleCalled.set(true);
            return Optional.of("TR1");
        });
        val ui = new UI(new TopologyEngine(topologyStore, scheduler, epochEventbus), MAPPER);

        val request = new SimpleTopologyCreateRequest("TEST_TOPO",
                                                      "0 0/1 * * * ?",
                                                      "docker.io/bash",
                                                      4,
                                                      512,
                                                      "test@x.com",
                                                      Map.of(),
                                                      List.of());
        val r = ui.createSimpleTopology(request);
        assertNotNull(r);
        assertTrue(saveCalled.get());
        assertTrue(scheduleCalled.get());
        assertFalse(updateCalled.get());

        when(topologyStore.update(anyString(), any())).thenAnswer(invocationMock -> {
            updateCalled.set(true);
            return Optional.of(details);
        });
        when(topologyStore.get(any())).thenAnswer(invocationMock -> Optional.of(details));
        scheduleCalled.set(false);
        saveCalled.set(false);
        val updateRequest = new SimpleTopologyEditRequest("0 0/1 * * * ?",
                                                          "docker.io/bash",
                                                          4,
                                                          512,
                                                          "test@x.com",
                                                          Map.of(),
                                                          List.of());
        val updateResponse = ui.updateTopology("TEST_TOPO", updateRequest);
        assertNotNull(updateResponse);
        assertTrue(updateCalled.get());
        assertTrue(scheduleCalled.get());
        assertFalse(saveCalled.get());
    }

    @Test
    void testHomeFailExists() {
        val topo = TestUtils.generateTopologyDesc(0);
        val details = EpochUtils.detailsFrom(topo);
        val saveCalled = new AtomicBoolean();
        when(topologyStore.get("TEST_TOPO")).thenReturn(Optional.of(detailsFrom(topo)));
        when(topologyStore.save(any())).thenAnswer(invocationMock -> {
            saveCalled.set(true);
            return Optional.of(details);
        });
        val ui = new UI(new TopologyEngine(topologyStore, scheduler, epochEventbus), MAPPER);

        val request = new SimpleTopologyCreateRequest("TEST_TOPO",
                                                      "* * * * *",
                                                      "docker.io/bash",
                                                      4,
                                                      512,
                                                      "test@x.com",
                                                      Map.of(),
                                                      List.of());
        val epochError = assertThrows(EpochError.class, () -> ui.createSimpleTopology(request));
        assertEquals(epochError.getErrorCode(), EpochErrorCode.INPUT_VALIDATION_ERROR);
        assertFalse(saveCalled.get());
    }

    @Test
    void testFailOnCreatingExistingTopologyOrUpdatingMissingTopology() {
        val topo = TestUtils.generateTopologyDesc(0);
        val details = EpochUtils.detailsFrom(topo);
        val saveCalled = new AtomicBoolean();
        val scheduleCalled = new AtomicBoolean();
        val updateCalled = new AtomicBoolean();
        when(topologyStore.save(any())).thenAnswer(invocationMock -> {
            saveCalled.set(true);
            return Optional.of(details);
        });
        when(scheduler.schedule(anyString(), anyString(), any(), any())).thenAnswer(invocationOnMock -> {
            scheduleCalled.set(true);
            return Optional.of("TR1");
        });
        val ui = new UI(new TopologyEngine(topologyStore, scheduler, epochEventbus), MAPPER);

        val request = new SimpleTopologyCreateRequest("TEST_TOPO",
                "0 0/1 * * * ?",
                "docker.io/bash",
                4,
                512,
                "test@x.com",
                Map.of(),
                List.of());
        val r = ui.createSimpleTopology(request);
        assertNotNull(r);
        assertTrue(saveCalled.get());
        assertTrue(scheduleCalled.get());
        assertFalse(updateCalled.get());

        when(topologyStore.get(any())).thenAnswer(invocationMock -> {
            return Optional.of(details);
        });

        val epochError = assertThrows(EpochError.class, () -> ui.createSimpleTopology(request));
        assertEquals(epochError.getErrorCode(), EpochErrorCode.TOPOLOGY_ALREADY_EXISTS);

        when(topologyStore.get(any())).thenAnswer(invocationMock -> {
            return Optional.empty();
        });

        val updateRequest = new SimpleTopologyEditRequest("0 0/1 * * * ?",
                "docker.io/bash",
                4,
                512,
                "test@x.com",
                Map.of(),
                List.of());
        val updateError = assertThrows(EpochError.class, () -> ui.updateTopology("TEST_TOPO", updateRequest));
        assertEquals(updateError.getErrorCode(), EpochErrorCode.TOPOLOGY_NOT_FOUND);
    }
}