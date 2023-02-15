package com.phonepe.epoch.server.resources;

import com.phonepe.epoch.models.topology.SimpleTopologyCreateRequest;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
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
//@ExtendWith(DropwizardExtensionsSupport.class)
class UITest extends TestBase {
    private static final TopologyStore topologyStore = mock(TopologyStore.class);
    private static final EpochEventBus epochEventbus = new EpochEventBus();

    private static final Scheduler scheduler = mock(Scheduler.class);

/*    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setMapper(MAPPER)
            .addResource()
            .addProvider(new AuthDynamicFeature(new DummyAuthFilter.Builder()
                                                        .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                                                        .setAuthorizer(new EpochAuthorizer())
                                                        .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(EpochUser.class))
            .addProvider(new ViewMessageBodyWriter(SharedMetricRegistries.getOrCreate("test"),
                                                   List.of(new HandlebarsViewRenderer())))
            .build();*/

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
        when(topologyStore.save(any())).thenAnswer(invocationMock -> {
            saveCalled.set(true);
            return Optional.of(details);
        });
        when(scheduler.schedule(anyString(), any(), any())).thenAnswer(invocationOnMock -> {
            scheduleCalled.set(true);
            return Optional.of("TR1");
        });
        val ui = new UI(topologyStore, scheduler, epochEventbus, MAPPER);

        val request = new SimpleTopologyCreateRequest("TEST_TOPO",
                                                      "* * * * *",
                                                      "docker.io/bash",
                                                      4,
                                                      512,
                                                      Map.of(),
                                                      List.of());
        val r = ui.createSimpleTopology(request);
        assertNotNull(r);
        assertTrue(saveCalled.get());
        assertTrue(scheduleCalled.get());
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
        val ui = new UI(topologyStore, scheduler, epochEventbus, MAPPER);

        val request = new SimpleTopologyCreateRequest("TEST_TOPO",
                                                      "* * * * *",
                                                      "docker.io/bash",
                                                      4,
                                                      512,
                                                      Map.of(),
                                                      List.of());
        val r = ui.createSimpleTopology(request);
        assertNotNull(r);
        assertFalse(saveCalled.get());
    }
}