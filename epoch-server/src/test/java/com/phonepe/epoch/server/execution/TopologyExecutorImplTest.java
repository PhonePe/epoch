package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;
import com.phonepe.epoch.server.store.InMemoryTopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.epoch.server.utils.EpochUtils.detailsFrom;
import static com.phonepe.epoch.server.utils.EpochUtils.topologyId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */
class TopologyExecutorImplTest extends TestBase {

    @Test
    void checkSingleTask() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        val ctr = new AtomicInteger(0);
        when(te.status(any(), any()))
                .thenAnswer((Answer<TaskStatusData>) invocationOnMock -> switch (ctr.incrementAndGet()) {
                    case 1 -> null;
                    case 2 -> throw new RuntimeException("Test exception");
                    default -> new TaskStatusData(EpochTaskRunState.COMPLETED, "");
                });
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = spy(new InMemoryTopologyRunInfoStore());
        when(tis.updateTaskState(anyString(), anyString(), anyString(), any(EpochTaskRunState.class), anyString()))
                .thenAnswer(new Answer<Optional<EpochTopologyRunInfo>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Optional<EpochTopologyRunInfo> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        if (ctr.get() == 3) {
                            throw new RuntimeException("Test update exception");
                        }
                        return (Optional<EpochTopologyRunInfo>) invocationOnMock.callRealMethod();
                    }
                });
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.SUCCESSFUL,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-1").getState());
    }
    @Test
    void checkSingleTaskPausedInstant() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.PAUSED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.INSTANT));
        assertEquals(EpochTopologyRunState.COMPLETED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-1").getState());
    }

    @Test
    void checkSingleTaskPaused() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.PAUSED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.SKIPPED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
    }

    @Test
    void checkSingleAlreadyRunningTask() {
        testPollingForState(EpochTaskRunState.RUNNING, EpochTopologyRunState.COMPLETED);
    }
    @Test
    void checkSingleAlreadyCompletedTask() {
        testPollingForState(EpochTaskRunState.COMPLETED, EpochTopologyRunState.COMPLETED);
    }

    @Test
    void checkSingleTaskFailedStart() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.PAUSED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.FAILED));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.INSTANT));
        assertEquals(EpochTopologyRunState.FAILED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.SUCCESSFUL));
    }

    @Test
    void checkSingleTaskStartException() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.PAUSED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenThrow(new RuntimeException("Test error"));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.INSTANT));
        assertEquals(EpochTopologyRunState.FAILED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.SUCCESSFUL));
    }

    @Test
    void checkSingleTaskDeleted() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.DELETED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.COMPLETED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
    }

    @Test
    void checkCompositeAllSuccess() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                    .<EpochTask>mapToObj(TestUtils::genContainerTask)
                                                                    .toList(),
                                                            EpochCompositeTask.CompositionType.ALL),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.SUCCESSFUL,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        IntStream.rangeClosed(1, 10)
                .forEach(i -> assertEquals(EpochTaskRunState.COMPLETED,
                                           tri.getTasks().get("test-task-" + i).getState()));
    }

    @Test
    void checkCompositeAllFail() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                    .<EpochTask>mapToObj(TestUtils::genContainerTask)
                                                                    .toList(),
                                                            EpochCompositeTask.CompositionType.ALL),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenAnswer((Answer<TaskStatusData>) invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(1, EpochContainerExecutionTask.class);
                    val parts = cmd.getTaskName().split("-");
                    val idx = Integer.parseInt(parts[parts.length - 1]);
                    return new TaskStatusData(idx % 2 == 1 ? EpochTaskRunState.COMPLETED : EpochTaskRunState.FAILED,
                                              "");
                });
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.FAILED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-1").getState());
        assertEquals(EpochTaskRunState.FAILED, tri.getTasks().get("test-task-2").getState());
        IntStream.rangeClosed(3, 10)
                .forEach(i -> assertEquals(EpochTaskRunState.PENDING, tri.getTasks().get("test-task-" + i).getState()));
    }

    @Test
    void checkCompositeAny() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     new EpochCompositeTask(IntStream.rangeClosed(1, 10)
                                                                    .<EpochTask>mapToObj(TestUtils::genContainerTask)
                                                                    .toList(),
                                                            EpochCompositeTask.CompositionType.ANY),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenAnswer((Answer<TaskStatusData>) invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(1, EpochContainerExecutionTask.class);
                    val parts = cmd.getTaskName().split("-");
                    val idx = Integer.parseInt(parts[parts.length - 1]);
                    return new TaskStatusData(idx == 10 ? EpochTaskRunState.COMPLETED : EpochTaskRunState.FAILED, "");
                });
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.SUCCESSFUL,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        IntStream.rangeClosed(3, 10)
                .forEach(i -> assertEquals(i == 10 ? EpochTaskRunState.COMPLETED : EpochTaskRunState.FAILED,
                                           tri.getTasks().get("test-task-" + i).getState()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failTopoStart() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = spy(new InMemoryTopologyRunInfoStore());
        val ctr = new AtomicInteger(0);
        doAnswer((Answer<Optional<EpochTopologyRunInfo>>) invocationOnMock -> {
            if (ctr.incrementAndGet() == 3) {
                throw new RuntimeException("Test exception");
            }
            return (Optional<EpochTopologyRunInfo>) invocationOnMock.callRealMethod();
        }).when(tis).get(anyString(), anyString());
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertEquals(EpochTopologyRunState.FAILED,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.SUCCESSFUL));
    }

    @Test
    void failTopoNone() {
        val topoName = "test-topo";
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.empty());
        val te = mock(TaskExecutionEngine.class);
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = new InMemoryTopologyRunInfoStore();
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertTrue(res.isEmpty());
    }

    @Test
    void failNoRunInfo() {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(detailsFrom(topo)));
        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val tis = mock(TopologyRunInfoStore.class);
        when(tis.get(anyString(), anyString())).thenReturn(Optional.empty());
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.SCHEDULED));
        assertTrue(res.isEmpty());
    }


    @SuppressWarnings("unchecked")
    private static void testPollingForState(EpochTaskRunState state, EpochTopologyRunState requiredState) {
        val topoName = "test-topo";
        val topo = new EpochTopology(topoName,
                                     TestUtils.genContainerTask(1),
                                     new EpochTaskTriggerCron("0/2 * * ? * * *"));
        val ts = mock(TopologyStore.class);
        when(ts.get(anyString())).thenReturn(Optional.of(new EpochTopologyDetails(topologyId(topo),
                                                                                  topo,
                                                                                  EpochTopologyState.PAUSED,
                                                                                  new Date(),
                                                                                  new Date())));


        val te = mock(TaskExecutionEngine.class);
        when(te.start(any(), any()))
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1")
                                    .setState(EpochTaskRunState.RUNNING));
        when(te.status(any(), any())).thenReturn(new TaskStatusData(EpochTaskRunState.COMPLETED, ""));
        when(te.cleanup(any(), any())).thenReturn(true);
        val ctr = new AtomicInteger();
        val tis = spy(new InMemoryTopologyRunInfoStore());
        doAnswer((Answer<Optional<EpochTopologyRunInfo>>) invocationOnMock -> {
            val res = (Optional<EpochTopologyRunInfo>) invocationOnMock.callRealMethod();
            if(ctr.getAndIncrement() == 0) {
                val run = res.orElse(null);
                val task = res.map(r -> r.getTasks().get("test-task-1")).orElse(null);
                Objects.requireNonNull(run, "Null run received");
                Objects.requireNonNull(task, "Null task received");
                return tis.forceTaskState(run.getTopologyId(), run.getRunId(), "test-task-1", state);
            }
            return res;
        }).when(tis).save(any());
        val eventBus = new EpochEventBus();
        val exec = new TopologyExecutorImpl(te, ts, tis, eventBus);

        val runId = UUID.randomUUID().toString();
        val res = exec.execute(new ExecuteCommand(runId, new Date(), topoName, EpochTopologyRunType.INSTANT));
        assertEquals(requiredState,
                     res.map(EpochTopologyRunInfo::getState).orElse(EpochTopologyRunState.FAILED));
        val tri = tis.get(topoName, runId).orElse(null);
        assertNotNull(tri);
        assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-1").getState());
    }
}