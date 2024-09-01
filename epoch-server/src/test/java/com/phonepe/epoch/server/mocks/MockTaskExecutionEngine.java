package com.phonepe.epoch.server.mocks;

import com.google.common.collect.Maps;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.execution.TaskStatusData;
import com.phonepe.epoch.server.remote.CancelResponse;
import com.phonepe.epoch.server.remote.TaskExecutionContext;
import com.phonepe.epoch.server.remote.TaskExecutionEngine;

import java.util.Map;

public class MockTaskExecutionEngine implements TaskExecutionEngine {
    private final Map<String, Map<String, TaskData>> topologyTasksCaptured = Maps.newConcurrentMap();

    public boolean setTaskState(String taskId, EpochTaskRunState state) {
        for (Map.Entry<String, Map<String, TaskData>> entry : topologyTasksCaptured.entrySet()) {
            final var taskMap = entry.getValue();
            final var taskData = taskMap.computeIfPresent(taskId,
                    (k, v) -> new TaskData(v.taskExecutionContext(), v.executionTask(), state));
            if (taskData != null) {
                return true;
            }
        }
        return false;
    }

    public int capturedTasksSize() {
        return topologyTasksCaptured.values().stream().mapToInt(m -> m.values().size()).sum();
    }

    public int capturedTasksSize(String topologyId) {
        if (!topologyTasksCaptured.containsKey(topologyId)) {
            return 0;
        }
        return topologyTasksCaptured.get(topologyId).size();
    }

    public void reset() {
        topologyTasksCaptured.clear();
    }

    @Override
    public EpochTopologyRunTaskInfo start(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        topologyTasksCaptured.putIfAbsent(context.getTopologyId(), Maps.newConcurrentMap());
        /* saving COMPLETED but returning pending, so that the next status check returns COMPLETED */
        topologyTasksCaptured.get(context.getTopologyId()).put(context.getRunId(),
                new TaskData(context, executionTask, EpochTaskRunState.COMPLETED));
        return new EpochTopologyRunTaskInfo()
                .setTaskId(context.getUpstreamTaskId())
                .setState(EpochTaskRunState.PENDING)
                .setUpstreamId(context.getUpstreamTaskId());
    }

    @Override
    public TaskStatusData status(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        final var capturedTasks = topologyTasksCaptured.get(context.getTopologyId());
        if (capturedTasks.containsKey(context.getRunId())) {
            return new TaskStatusData(capturedTasks.get(context.getRunId()).state(), "");
        }
        return new TaskStatusData(EpochTaskRunState.UNKNOWN, "Not found");
    }

    @Override
    public CancelResponse cancelTask(String taskId) {
        for (Map.Entry<String, Map<String, TaskData>> entry : topologyTasksCaptured.entrySet()) {
            for (Map.Entry<String, TaskData> taskEntry : entry.getValue().entrySet()) {
                if (taskEntry.getValue().taskExecutionContext().getUpstreamTaskId().equals(taskId)) {
                    entry.getValue().remove(taskEntry.getKey());
                    return new CancelResponse(true, "Task removed");
                }
            }
        }
        return new CancelResponse(false, "Task not found");
    }

    @Override
    public boolean cleanupTask(String upstreamTaskId) {
        for (Map.Entry<String, Map<String, TaskData>> entry : topologyTasksCaptured.entrySet()) {
            for (Map.Entry<String, TaskData> taskEntry : entry.getValue().entrySet()) {
                if (taskEntry.getValue().taskExecutionContext().getUpstreamTaskId().equals(upstreamTaskId)) {
                    entry.getValue().remove(taskEntry.getKey());
                    return true;
                }
            }
        }
        return false;
    }

    record TaskData(
            TaskExecutionContext taskExecutionContext,
            EpochContainerExecutionTask executionTask,
            EpochTaskRunState state) {
    }
}
