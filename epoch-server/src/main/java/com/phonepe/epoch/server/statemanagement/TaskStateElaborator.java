package com.phonepe.epoch.server.statemanagement;

import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.tasks.EpochTaskVisitor;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import lombok.val;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class TaskStateElaborator implements EpochTaskVisitor<Void> {

    private final Map<String, EpochTopologyRunTaskInfo> states;

    public TaskStateElaborator() {
        this(new HashMap<>());
    }

    public TaskStateElaborator(Map<String, EpochTopologyRunTaskInfo> states) {
        this.states = states;
    }

    public Map<String, EpochTopologyRunTaskInfo> states(final EpochTask task) {
        task.accept(this);
        return states;
    }

    @Override
    public Void visit(EpochCompositeTask composite) {
        composite.getTasks().forEach(task -> task.accept(this));
        return null;
    }

    @Override
    public Void visit(EpochContainerExecutionTask containerExecution) {
        val taskName = containerExecution.getTaskName();
        states.put(taskName,
                   states.getOrDefault(taskName, new EpochTopologyRunTaskInfo()
                           .setState(EpochTaskRunState.PENDING)));
        return null;
    }
}
