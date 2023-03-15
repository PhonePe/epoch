package com.phonepe.epoch.server.resources;

import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.managed.DroveClientManager;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.remote.CancelResponse;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.phonepe.epoch.models.topology.EpochTopologyState.ACTIVE;
import static com.phonepe.epoch.models.topology.EpochTopologyState.PAUSED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ApisTest extends TestBase {
    private static final TopologyStore topologyStore = mock(TopologyStore.class);
    private static final TopologyRunInfoStore topologyRunInfoStore = mock(TopologyRunInfoStore.class);

    private static final Scheduler scheduler = mock(Scheduler.class);

    private static final DroveClientManager droveClientManager = mock(DroveClientManager.class);

    private static final TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);


    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setMapper(MAPPER)
            .addResource(new Apis(topologyStore,
                                  topologyRunInfoStore,
                                  scheduler,
                                  droveClientManager,
                                  taskExecutionEngine))
            .build();

    @AfterEach
    void reset() {
        Mockito.reset(topologyStore);
        Mockito.reset(topologyRunInfoStore);
        Mockito.reset(scheduler);
        Mockito.reset(droveClientManager);
        Mockito.reset(taskExecutionEngine);
    }

    @Test
    void testSaveSuccess() {
        when(topologyStore.get(anyString())).thenReturn(Optional.empty());
        when(topologyStore.save(any()))
                .thenAnswer(invocationMock -> Optional.of(EpochUtils.detailsFrom(
                        invocationMock.getArgument(0, EpochTopology.class))));
        when(scheduler.schedule(anyString(), any(), any())).thenReturn(Optional.of("TestSched"));
        val topology = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        try (val r = EXT.target("/v1/topologies")
                .request()
                .post(Entity.json(topology))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals(topology, apiR.getData().getTopology());
        }
    }

    @Test
    void testSaveFailExists() {
        val topology = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        when(topologyStore.get(anyString())).thenReturn(Optional.of(EpochUtils.detailsFrom(topology)));
        try (val r = EXT.target("/v1/topologies")
                .request()
                .post(Entity.json(topology))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testFailSave() {
        when(topologyStore.get(anyString())).thenReturn(Optional.empty());
        when(topologyStore.save(any())).thenReturn(Optional.empty());
        val topology = TestUtils.generateTopologyDesc(1, new MailNotificationSpec(List.of("test@email.com")));
        try (val r = EXT.target("/v1/topologies")
                .request()
                .post(Entity.json(topology))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testListTopologiesEmpty() {
        when(topologyStore.list(any())).thenReturn(List.of());
        try (val r = EXT.target("/v1/topologies")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<List<EpochTopologyDetails>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertTrue(apiR.getData().isEmpty());
        }
    }

    @Test
    void testListTopologiesNonEmpty() {
        when(topologyStore.list(any()))
                .thenReturn(IntStream.rangeClosed(1, 10)
                                    .mapToObj(i -> EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(i, new MailNotificationSpec(List.of("test@email.com")))))
                                    .toList());
        try (val r = EXT.target("/v1/topologies")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<List<EpochTopologyDetails>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertFalse(apiR.getData().isEmpty());
        }
    }

    @Test
    void testGetTopologySuccess() {
        val details = EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(0, new MailNotificationSpec(List.of("test@email.com"))));
        when(topologyStore.get(anyString()))
                .thenReturn(Optional.of(details));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals(details, apiR.getData());
        }
    }

    @Test
    void testGetTopologyNotPresent() {
        when(topologyStore.get(anyString()))
                .thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testRunTopologySuccess() {
        val details = EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(0));
        when(topologyStore.get(anyString()))
                .thenReturn(Optional.of(details));
        when(scheduler.scheduleNow(details.getId()))
                .thenReturn(Optional.of("TEST_RUN"));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/run")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Map<String, String>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals("TEST_RUN", apiR.getData().get("runId"));
        }
    }

    @Test
    void testRunTopologyFailSchedule() {
        val details = EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(0));
        when(topologyStore.get(anyString()))
                .thenReturn(Optional.of(details));
        when(scheduler.scheduleNow(details.getId()))
                .thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/run")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Map<String, String>>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testRunTopologyFailNoTopology() {
        when(topologyStore.get(anyString()))
                .thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/run")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Map<String, String>>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testPauseTopologySuccess() {
        val details = EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(0, new MailNotificationSpec(List.of("test@email.com"))));
        when(topologyStore.updateState(details.getId(), PAUSED))
                .thenAnswer(invocationMock -> Optional.of(new EpochTopologyDetails(details.getId(),
                                                                                   details.getTopology(),
                                                                                   PAUSED,
                                                                                   details.getCreated(),
                                                                                   new Date())));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/pause")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals(PAUSED, apiR.getData().getState());
        }
    }

    @Test
    void testPauseTopologyFail() {
        when(topologyStore.updateState(anyString(), any(EpochTopologyState.class))).thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/pause")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testUnpauseTopologySuccess() {
        val details = EpochUtils.detailsFrom(TestUtils.generateTopologyDesc(0, new MailNotificationSpec(List.of("test@email.com"))));
        val called = new AtomicBoolean();
        when(topologyStore.updateState(details.getId(), ACTIVE))
                .thenAnswer(invocationMock -> {
                    called.set(true);
                    return Optional.of(details);
                });
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/unpause")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertTrue(called.get());
        }
    }

    @Test
    void testUnpauseTopologyFail() {
        when(topologyStore.updateState(anyString(), any(EpochTopologyState.class))).thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0/unpause")
                .request()
                .put(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyDetails>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testDeleteTopologySuccess() {
        when(topologyStore.delete("TEST_TOPO-0")).thenReturn(true);
        when(topologyRunInfoStore.deleteAll("TEST_TOPO-0")).thenReturn(true);
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0")
                .request()
                .delete()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Boolean>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertTrue(apiR.getData());
        }
    }

    @Test
    void testDeleteTopologyFailDeleteRuns() {
        when(topologyStore.delete("TEST_TOPO-0")).thenReturn(true);
        when(topologyRunInfoStore.deleteAll("TEST_TOPO-0")).thenReturn(false);
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0")
                .request()
                .delete()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Boolean>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertFalse(apiR.getData());
        }
    }

    @Test
    void testDeleteTopologyFailDeleteTopology() {
        when(topologyStore.delete("TEST_TOPO-0")).thenReturn(false);
        try (val r = EXT.target("/v1/topologies/TEST_TOPO-0")
                .request()
                .delete()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Boolean>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertFalse(apiR.getData());
        }
    }

    @Test
    void testListRunsNoParamSuccess() {
        when(topologyRunInfoStore.list(eq("TEST_TOPO"), any()))
                .thenReturn(IntStream.rangeClosed(1,10)
                                    .mapToObj(i -> TestUtils.genRunInfo(i, EpochTopologyRunState.RUNNING))
                                    .toList());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Collection<EpochTopologyRunInfo>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals(10, apiR.getData().size());
        }
    }

    @Test
    void testListRunsMultiParamSuccess() {
        when(topologyRunInfoStore.list(eq("TEST_TOPO"), any()))
                .thenReturn(IntStream.rangeClosed(1,10)
                                    .mapToObj(i -> TestUtils.genRunInfo(i, EpochTopologyRunState.RUNNING))
                                    .toList());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs")
                .queryParam("state", EpochTaskRunState.RUNNING)
                .queryParam("state", EpochTaskRunState.COMPLETED)
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Collection<EpochTopologyRunInfo>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals(10, apiR.getData().size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListRunsMultiParamNoMatch() {
        when(topologyRunInfoStore.list(eq("TEST_TOPO"), any()))
                .thenAnswer(invocationMock -> {
                    val raw = IntStream.rangeClosed(1,10)
                            .mapToObj(i -> TestUtils.genRunInfo(i, EpochTopologyRunState.SUCCESSFUL))
                            .toList();
                    val test = (Predicate<EpochTopologyRunInfo>)invocationMock.getArgument(1);
                    return raw.stream().filter(test).toList();
                });
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs")
                .queryParam("state", EpochTaskRunState.RUNNING)
                .queryParam("state", EpochTaskRunState.COMPLETED)
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<Collection<EpochTopologyRunInfo>>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertTrue(apiR.getData().isEmpty());
        }
    }

    @Test
    void testGetRunSuccess() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1")
                .queryParam("state", EpochTaskRunState.RUNNING)
                .queryParam("state", EpochTaskRunState.COMPLETED)
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyRunInfo>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertNotNull(apiR.getData());
        }
    }

    @Test
    void testGetRunFailure() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-2")
                .queryParam("state", EpochTaskRunState.RUNNING)
                .queryParam("state", EpochTaskRunState.COMPLETED)
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<EpochTopologyRunInfo>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertNull(apiR.getData());
        }
    }

    @Test
    void testKillTaskSuccess() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        when(taskExecutionEngine.cancelTask("TR-T-0")).thenReturn(new CancelResponse(true, "Success"));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/kill")
                .request()
                .post(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<CancelResponse>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertTrue(apiR.getData().isSuccess());
        }
    }

    @Test
    void testKillTaskCancelFailure() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        when(taskExecutionEngine.cancelTask("TR-T-0")).thenReturn(new CancelResponse(false, "Test Failure"));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/kill")
                .request()
                .post(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<CancelResponse>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertFalse(apiR.getData().isSuccess());
            assertEquals("Test Failure", apiR.getData().getMessage());
        }
    }

    @Test
    void testKillTaskNoElementFailure() {
        when(topologyRunInfoStore.get(anyString(), anyString())).thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/kill")
                .request()
                .post(Entity.json(""))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<CancelResponse>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
            assertFalse(apiR.getData().isSuccess());
            assertEquals("No task found for the provided id", apiR.getData().getMessage());
        }
    }

    @Test
    void testLogLinkSuccess() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        val dc = mock(DroveClient.class);
        when(droveClientManager.getClient()).thenReturn(dc);
        when(dc.leader()).thenReturn(Optional.of("http://localhost:8080"));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/log")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<URI>>() {
            });
            assertEquals(ApiErrorCode.SUCCESS, apiR.getStatus());
            assertEquals("http://localhost:8080/tasks/epoch.test/TDT-0", apiR.getData().toString());
        }
    }

    @Test
    void testLogLinkNoLeaderFail() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        val dc = mock(DroveClient.class);
        when(droveClientManager.getClient()).thenReturn(dc);
        when(dc.leader()).thenReturn(Optional.empty());
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/log")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<URI>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
        }
    }

    @Test
    void testLogLinkNoRunInfoFail() {
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-0/log")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<URI>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
        }
    }

    @Test
    void testLogLinkNoTaskFail() {
        when(topologyRunInfoStore.get("TEST_TOPO", "TR-1"))
                .thenReturn(Optional.of(TestUtils.genRunInfo(0, EpochTopologyRunState.RUNNING)));
        try (val r = EXT.target("/v1/topologies/TEST_TOPO/runs/TR-1/tasks/TR-T-1/log")
                .request()
                .get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
            val apiR = r.readEntity(new GenericType<ApiResponse<URI>>() {
            });
            assertEquals(ApiErrorCode.FAILED, apiR.getStatus());
        }
    }
}