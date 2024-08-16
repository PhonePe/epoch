package com.phonepe.epoch.server.remote;

import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
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
import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopology;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.config.DroveConfig;
import com.phonepe.epoch.server.managed.DroveClientManager;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.util.Duration;
import io.dropwizard.util.Strings;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@WireMockTest
class DroveTaskExecutionEngineTest extends TestBase {
    @Test
    void testStartSuccess(WireMockRuntimeInfo wm) {
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(ok(toString(ApiResponse.success(Map.of("taskId", "Test_1"))))));
        val engine = createEngine(wm);

        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
    void testStartServerError(WireMockRuntimeInfo wm) {
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(serverError()));
        val engine = createEngine(wm);

        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
    @SneakyThrows
    void testStartFailed(WireMockRuntimeInfo wm) {
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.failure("Test failure")))));
        val engine = createEngine(wm);

        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        val engine = createEngine(wm);

        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(serverError()));
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(serverError()));
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
    void testStartExistingException(WireMockRuntimeInfo wm) {
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(
                                badRequest()
                                        .withResponseBody(new Body(
                                                toString(ApiResponse.failure(Map.of("validationErrors",
                                                                                    List.of("Task already exists ")),
                                                                             "Bad request"))))));
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(serverError()));
        val engine = createEngine(wm);
        val task = new EpochContainerExecutionTask("CT1",
                                                   new DockerCoordinates("docker.io/bash",
                                                                         Duration.seconds(2)),
                                                   List.of(new CPURequirement(1),
                                                           new MemoryRequirement(1)),
                                                   List.of(),
                                                   List.of(),
                                                   LocalLoggingSpec.DEFAULT,
                                                   new AnyPlacementPolicy(),
                                                   Map.of(),
                                                   Map.of(),
                                                   List.of());
        val spec = new EpochTopology("Test",
                                     task,
                                     new EpochTaskTriggerAt(new Date()),
                                     BlackholeNotificationSpec.DEFAULT);
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
    void testStatus(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
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

        val context = new TaskExecutionContext(EpochUtils.topologyId("Test"),
                                               "TR1",
                                               "CT1",
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);

        val status = engine.status(context, null);
        assertEquals(EpochTaskRunState.RUNNING, status.state());
        assertTrue(Strings.isNullOrEmpty(status.errorMessage()));
    }

    @Test
    void testStatusUnknown(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(notFound()));

        val context = new TaskExecutionContext(EpochUtils.topologyId("Test"),
                                               "TR1",
                                               "CT1",
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);

        val status = engine.status(context, null);
        assertEquals(EpochTaskRunState.UNKNOWN, status.state());
        assertEquals("Status could not be ascertained. Task might not have started yet",
                     status.errorMessage());
    }

    @Test
    void testStatusFailPermanently(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(get("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        val context = new TaskExecutionContext(EpochUtils.topologyId("Test"),
                                               "TR1",
                                               "CT1",
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);

        val status = engine.status(context, null);
        assertEquals(EpochTaskRunState.UNKNOWN, status.state());
        assertEquals("Status could not be ascertained. Task might not have started yet",
                     status.errorMessage());
    }

    @Test
    void testStatusFailException(WireMockRuntimeInfo wm) {
        val engine = createFailEngine(wm);

        val context = new TaskExecutionContext(EpochUtils.topologyId("Test"),
                                               "TR1",
                                               "CT1",
                                               EpochTopologyRunType.INSTANT,
                                               EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);

        val status = engine.status(context, null);
        assertEquals(EpochTaskRunState.UNKNOWN, status.state());
        assertEquals("Error getting task status: Failure testing error",
                     status.errorMessage());
    }

    @Test
    @SneakyThrows
    void testCleanupSuccess(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(delete("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.success(Map.of("deleted", true))))));
        assertTrue(engine.cleanup(""));
        assertTrue(engine.cleanup(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID));
        assertTrue(engine.cleanup("Test-TR1-CT1"));
    }

    @Test
    @SneakyThrows
    void testCleanupFailDelete(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(delete("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.success(Map.of("deleted", false))))));
        assertFalse(engine.cleanup("Test-TR1-CT1"));
    }

    @Test
    @SneakyThrows
    void testCleanupFailure(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(delete("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.failure("Test fail")))));
        assertFalse(engine.cleanup("Test-TR1-CT1"));
        assertFalse(engine.cleanup(new TaskExecutionContext(null, null, null, null, "Test-TR1-CT1"), null));
    }

    @Test
    @SneakyThrows
    void testCleanupInfo(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(delete("/apis/v1/tasks/epoch.test/instances/TDT-0")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.success(Map.of("deleted", true))))));
        val runInfo = TestUtils.genRunInfo(0, EpochTopologyRunState.COMPLETED);
        assertTrue(engine.cleanup(runInfo));
    }

    @Test
    @SneakyThrows
    void testCleanupNetworkError(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(delete("/apis/v1/tasks/epoch.test/instances/Test-TR1-CT1")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        assertFalse(engine.cleanup("Test-TR1-CT1"));
    }

    @Test
    @SneakyThrows
    void testCleanupException(WireMockRuntimeInfo wm) {
        val engine = createFailEngine(wm);
        assertFalse(engine.cleanup("Test-TR1-CT1"));
    }

    @Test
    @SneakyThrows
    void testCancelTask(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.success(Map.of())))));
        val cr = engine.cancelTask("Random");
        assertTrue(cr.isSuccess());
        assertEquals("Task cancel accepted", cr.getMessage());
    }

    @Test
    @SneakyThrows
    void testCancelTaskFailure(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(ok(MAPPER.writeValueAsString(ApiResponse.failure("Test failure")))));
        val cr = engine.cancelTask("Random");
        assertFalse(cr.isSuccess());
        assertEquals("Task cancellation failed with error: Test failure", cr.getMessage());
    }

    @Test
    @SneakyThrows
    void testCancelTaskFailureBadRequest(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(badRequest()
                                            .withBody(MAPPER.writeValueAsString(ApiResponse.failure("Test failure")))));
        val cr = engine.cancelTask("Random");
        assertFalse(cr.isSuccess());
        assertEquals("Task cancellation failed with error: Test failure", cr.getMessage());
    }

    @Test
    @SneakyThrows
    void testCancelTaskFailureServerError(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(serverError()
                                            .withBody(MAPPER.writeValueAsString(ApiResponse.failure("Test failure")))));
        val cr = engine.cancelTask("Random");
        assertFalse(cr.isSuccess());
        assertEquals("Task cancellation failed with status: [500] {\"status\":\"FAILED\",\"message\":\"Test failure\"}",
                     cr.getMessage());
    }

    @Test
    @SneakyThrows
    void testCancelTaskFailureNetworkError(WireMockRuntimeInfo wm) {
        val engine = createEngine(wm);
        stubFor(post("/apis/v1/tasks/operations")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val cr = engine.cancelTask("Random");
        assertFalse(cr.isSuccess());
        assertEquals("Could not send kill command to drove", cr.getMessage());
    }

    @Test
    @SneakyThrows
    void testCancelTaskFailureException(WireMockRuntimeInfo wm) {
        val engine = createFailEngine(wm);

        val cr = engine.cancelTask("Random");
        assertFalse(cr.isSuccess());
        assertEquals("Task cancellation failed with error: Failure testing error", cr.getMessage());
    }

    private static DroveTaskExecutionEngine createEngine(WireMockRuntimeInfo wm) {
        val config = new DroveConfig()
                .setEndpoints(List.of(wm.getHttpBaseUrl()))
                .setRpcRetryCount(3)
                .setRpcRetryInterval(Duration.milliseconds(10));
        val client = new DroveClientManager(config);
        return new DroveTaskExecutionEngine(client, MAPPER);
    }

    private static DroveTaskExecutionEngine createFailEngine(WireMockRuntimeInfo wm) {
        val config = new DroveConfig()
                .setEndpoints(List.of(wm.getHttpBaseUrl()))
                .setRpcRetryCount(3)
                .setRpcRetryInterval(Duration.milliseconds(10));
        val client = mock(DroveClientManager.class);
        val dc = mock(DroveClient.class);
        when(client.getDroveConfig()).thenReturn(config);
        when(client.getClient()).thenReturn(dc);
        when(dc.execute(ArgumentMatchers.any(DroveClient.Request.class)))
                .thenThrow(new RuntimeException("Failure testing error"));
        return new DroveTaskExecutionEngine(client, MAPPER);
    }
}