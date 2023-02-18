package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.server.execution.ExecuteCommand;
import com.phonepe.epoch.server.execution.ExecutionTimeCalculator;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.store.TopologyStore;
import io.appform.kaal.*;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 *
 */
@Slf4j
@Singleton
@Order(40)
public class Scheduler implements Managed {
    private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private final TopologyExecutor topologyExecutor;

    private final ExecutionTimeCalculator timeCalculator = new ExecutionTimeCalculator();

    private final ConsumingFireForgetSignal<TaskData> taskCompleted = new ConsumingFireForgetSignal<>();

    private static final class EpochRunnableTask implements KaalTask<EpochRunnableTask, TaskData> {

        private final String topologyId;
        private final EpochTaskTrigger trigger;
        private final ExecutionTimeCalculator timeCalculator;
        private final EpochTopologyRunType runType;
        private final TopologyExecutor topologyExecutor;

        private EpochRunnableTask(
                String topologyId, EpochTaskTrigger trigger,
                ExecutionTimeCalculator timeCalculator, EpochTopologyRunType runType, TopologyExecutor topologyExecutor) {
            this.topologyId = topologyId;
            this.trigger = trigger;
            this.timeCalculator = timeCalculator;
            this.runType = runType;
            this.topologyExecutor = topologyExecutor;
        }

        @Override
        public String id() {
            return topologyId;
        }

        @Override
        public long delayToNextRun(Date currentTime) {
            if (null == trigger) {
                return -1L;
            }
            return timeCalculator.executionTime(trigger, currentTime)
                    .map(Duration::toMillis)
                    .filter(value -> value >= 0)
                    .orElse(-1L);
        }

        @Override
        public TaskData apply(Date currentTime, KaalTaskData<EpochRunnableTask, TaskData> runData) {
            val task = runData.getTask();
            val taskId = task.id();
            val runId = runData.getRunId();
            val executionTime = runData.getTargetExecutionTime();
            log.trace("Received exec command for: {}/{}", taskId, runId);
            return new TaskData(topologyId,
                         topologyExecutor.execute(new ExecuteCommand(runId, executionTime, topologyId, runType)).orElse(null),
                         runType);
        }
    }

    private static final class EpochTaskStopStrategy implements KaalTaskStopStrategy<EpochRunnableTask, TaskData> {

        private final TopologyStore topologyStore;
        private final LeadershipManager leadershipManager;

        private EpochTaskStopStrategy(TopologyStore topologyStore, LeadershipManager leadershipManager) {
            this.topologyStore = topologyStore;
            this.leadershipManager = leadershipManager;
        }

        @Override
        public boolean scheduleNext(KaalTaskData<EpochRunnableTask, TaskData> lastRunData) {
            if(!leadershipManager.isLeader()) {
                log.info("Will not reschedule any task as I'm not the leader");
                return false;
            }
            val taskData = lastRunData.getResult();
            val tId = taskData.topologyId();
            val rId = taskData.runInfo().getRunId();

            val result = taskData.runInfo().getState();
            if (taskData.runType() == EpochTopologyRunType.INSTANT) {
                log.info("Run {}/{} finished with state: {}. No more runs needed as this was an instant run.",
                         tId,
                         rId,
                         result);
                return false;
            }
            if (result == EpochTopologyRunState.COMPLETED) {
                log.info("No further scheduling needed for {}/{}", tId, rId);
                return false;
            }
            val trigger = topologyStore.get(tId)
                    .map(e -> e.getTopology().getTrigger())
                    .orElse(null);
            if (trigger == null) {
                log.info("Topology {} seems to have been deleted, no scheduling needed.", tId);
                return false;
            }
            return true;
        }
    }

    private static final class EpochTaskIdGenerator implements KaalTaskIdGenerator<EpochRunnableTask, TaskData> {

        @Override
        public String generateId(EpochRunnableTask task, Date executionTime) {
            return (task.runType == EpochTopologyRunType.INSTANT ? "EIR-" : "ESR-")
                    + new SimpleDateFormat(DATE_FORMAT).format(executionTime);
        }
    }

    public record TaskData(
            String topologyId,
            EpochTopologyRunInfo runInfo,
            EpochTopologyRunType runType
    ) {
    }

    private final KaalScheduler<EpochRunnableTask, TaskData> schedulerImpl;

    @Inject
    public Scheduler(
            @Named("taskPool") ExecutorService executorService,
            TopologyStore topologyStore,
            TopologyExecutor topologyExecutor,
            LeadershipManager leadershipManager) {
        this.topologyExecutor = topologyExecutor;
        this.schedulerImpl = KaalScheduler.<EpochRunnableTask, TaskData>builder()
                .withExecutorService(executorService)
                .withTaskStopStrategy(new EpochTaskStopStrategy(topologyStore, leadershipManager))
                .withTaskIdGenerator(new EpochTaskIdGenerator())
                .withPollingInterval(100)
                .build();
        this.schedulerImpl.onTaskCompleted()
                .connect(taskData -> taskCompleted.dispatch(taskData.getResult()));
    }

    @Override
    public void start() throws Exception {
        schedulerImpl.start();
        log.info("Started task scheduler");
    }

    @Override
    public void stop() throws Exception {
        schedulerImpl.stop();
        log.info("Task scheduler shut down");
    }

    public void clear() {
        schedulerImpl.clear();
    }

    public ConsumingFireForgetSignal<TaskData> taskCompleted() {
        return taskCompleted;
    }

    public Optional<String> schedule(
            final String topologyId,
            final EpochTaskTrigger trigger,
            Date currTime) {
        return schedule(topologyId, trigger, currTime, EpochTopologyRunType.SCHEDULED);
    }

    public Optional<String> scheduleNow(String topologyId) {
        val currTime = new Date();
        return schedule(topologyId, new EpochTaskTriggerAt(currTime), currTime, EpochTopologyRunType.INSTANT);
    }

    public boolean recover(
            String topologyId,
            String runId,
            Date currTime,
            EpochTopologyRunType runType) {
        return schedulerImpl.schedule(new EpochRunnableTask(topologyId, null, timeCalculator, runType, topologyExecutor),
                                      currTime,
                                      currTime,
                                      runId,
                                      0)
                .isPresent();
    }

    private Optional<String> schedule(
            final String topologyId,
            final EpochTaskTrigger trigger,
            Date currTime,
            EpochTopologyRunType runType) {
        return schedulerImpl.schedule(
                new EpochRunnableTask(topologyId, trigger, timeCalculator, runType, topologyExecutor),
                currTime);
    }
}
