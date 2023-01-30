package com.phonepe.epoch.server.remote;

import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.config.DroveConfig;
import com.phonepe.epoch.server.managed.DroveClientManager;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class DroveTaskExecutionEngineTest extends TestBase {
    @Test
    void testStartSuccess(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(ok(toString(ApiResponse.success(Map.of("taskId", "Test_1"))))));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        val runInfo = engine.start(context, task);
        assertEquals("Test-TR1-CT1", runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.STARTING, runInfo.getState());
    }

    @Test
    void testStartFailed(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(serverError()));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        val runInfo = engine.start(context, task);
        assertEquals(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID, runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.FAILED, runInfo.getState());
    }

    @Test
    void testStartUIDAvailable(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(ok(toString(
                                ApiResponse.success(
                                        new TaskInfo("epoch.test",
                                                     "Test-TR1-CT1",
                                                     "CT1",
                                                     "EX1",
                                                     "test-executor-001",
                                                     new DockerCoordinates("docker.io/bash",
                                                                           Duration.seconds(2)),
                                                     List.of(new CPUAllocation(Map.of(0,
                                                                                      Set.of(1))),
                                                             new MemoryAllocation(Map.of(0, 1L))),
                                                     List.of(),
                                                     LocalLoggingSpec.DEFAULT,
                                                     Map.of(),
                                                     TaskState.RUNNING,
                                                     Map.of(),
                                                     null,
                                                     "",
                                                     new Date(),
                                                     new Date()))))));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               "Test-TR1-CT1");
        val runInfo = engine.start(context, task);
        assertEquals("Test-TR1-CT1", runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.RUNNING, runInfo.getState());
    }

    @Test
    void testStartUIDAvailableServerError(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(serverError()));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               "Test-TR1-CT1");
        val runInfo = engine.start(context, task);
        assertEquals("Test-TR1-CT1", runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.UNKNOWN, runInfo.getState());
    }

    @Test
    void testStartUIDAvailableException(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               "Test-TR1-CT1");
        val runInfo = engine.start(context, task);
        assertEquals("Test-TR1-CT1", runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.UNKNOWN, runInfo.getState());
    }

    @Test
    void testStartExisting(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(ok(toString(
                                ApiResponse.success(
                                        new TaskInfo("epoch.test",
                                                     "Test-TR1-CT1",
                                                     "CT1",
                                                     "EX1",
                                                     "test-executor-001",
                                                     new DockerCoordinates("docker.io/bash",
                                                                           Duration.seconds(2)),
                                                     List.of(new CPUAllocation(Map.of(0,
                                                                                      Set.of(1))),
                                                             new MemoryAllocation(Map.of(0, 1L))),
                                                     List.of(),
                                                     LocalLoggingSpec.DEFAULT,
                                                     Map.of(),
                                                     TaskState.RUNNING,
                                                     Map.of(),
                                                     null,
                                                     "",
                                                     new Date(),
                                                     new Date()))))));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        val runInfo = engine.start(context, task);
        assertEquals("Test-TR1-CT1", runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.RUNNING, runInfo.getState());
    }

    @Test
    void testStartExistingServerError(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(serverError()));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        val runInfo = engine.start(context, task);
        assertEquals(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID, runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.UNKNOWN, runInfo.getState());
    }

    @Test
    void testStartExistingNetworkError(WireMockRuntimeInfo wm) {
        val config = new DroveConfig().setEndpoints(List.of(wm.getHttpBaseUrl()));
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val client = new DroveClientManager(config);
        val engine = new DroveTaskExecutionEngine(client, MAPPER);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()));
        val context = new TaskExecutionContext(EpochUtils.topologyId(spec.getName()),
                                               "TR1",
                                               task.getTaskName(),
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        val runInfo = engine.start(context, task);
        assertEquals(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID, runInfo.getUpstreamId());
        assertEquals(EpochTaskRunState.UNKNOWN, runInfo.getState());
    }
}