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
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.phonepe.epoch.server.utils.EpochUtils.detailsFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1").setState(EpochTaskRunState.STARTING));
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
        assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-1").getState());
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
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1").setState(EpochTaskRunState.STARTING));
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
                .forEach(i -> assertEquals(EpochTaskRunState.COMPLETED, tri.getTasks().get("test-task-" + i).getState()));
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
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1").setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenAnswer((Answer<TaskStatusData>) invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(1, EpochContainerExecutionTask.class);
                    val parts = cmd.getTaskName().split("-");
                    val idx = Integer.parseInt(parts[parts.length -1]);
                    return new TaskStatusData(idx % 2 == 1 ? EpochTaskRunState.COMPLETED : EpochTaskRunState.FAILED, "");
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
                .thenReturn(new EpochTopologyRunTaskInfo().setUpstreamId("TEST_1").setState(EpochTaskRunState.STARTING));
        when(te.status(any(), any()))
                .thenAnswer((Answer<TaskStatusData>) invocationOnMock -> {
                    val cmd = invocationOnMock.getArgument(1, EpochContainerExecutionTask.class);
                    val parts = cmd.getTaskName().split("-");
                    val idx = Integer.parseInt(parts[parts.length -1]);
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


}