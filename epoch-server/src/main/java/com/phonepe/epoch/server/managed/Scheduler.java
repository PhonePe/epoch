package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunType;
import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import com.phonepe.epoch.server.execution.ExecuteCommand;
import com.phonepe.epoch.server.execution.ExecutionTimeCalculator;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.store.TopologyStore;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;

/**
 *
 */
@Slf4j
@Singleton
@Order(40)
public final class Scheduler implements Managed {
    private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private static final String HANDLER_NAME = "TASK_POLLER";

    private final ScheduledSignal signalGenerator = ScheduledSignal.builder()
            .errorHandler(e -> log.error("Error running scheduled poll: " + e.getMessage(), e))
            .interval(Duration.ofSeconds(1))
            .build();
    private final ExecutorService executorService;
    private final TopologyStore topologyStore;
    private final TopologyExecutor topologyExecutor;
    private final LeadershipManager leadershipManager;

    private final PriorityBlockingQueue<ExecuteCommand> tasks
            = new PriorityBlockingQueue<>(1024, Comparator.comparing(ExecuteCommand::getNextExecutionTime));
    private final ExecutionTimeCalculator timeCalculator = new ExecutionTimeCalculator();

    private final ConsumingFireForgetSignal<TaskData> taskCompleted = new ConsumingFireForgetSignal<>();

    public record TaskData(
            String topologyId,
            EpochTopologyRunInfo runInfo,
            EpochTopologyRunType runType
    ) { }

    @Inject
    public Scheduler(@Named("taskPool") ExecutorService executorService,
                     TopologyStore topologyStore,
                     TopologyExecutor topologyExecutor,
                     LeadershipManager leadershipManager) {
        this.executorService = executorService;
        this.topologyStore = topologyStore;
        this.topologyExecutor = topologyExecutor;
        this.leadershipManager = leadershipManager;
        this.leadershipManager.onGainingLeadership()
                .connect(value -> {
                    log.info("Purging scheduler queue for a fresh start");
                    tasks.clear();
                });
    }

    @Override
    public void start() throws Exception {
        taskCompleted.connect(this::handleTaskCompletion);
        signalGenerator.connect(this::check);
        log.info("Started task monitor");
    }

    @Override
    public void stop() throws Exception {
        signalGenerator.disconnect(HANDLER_NAME);
        signalGenerator.close();
        log.info("Task monitor shut down");
    }

    public ConsumingFireForgetSignal<TaskData> taskCompleted() {
        return taskCompleted;
    }

    public boolean schedule(final String topologyId,
                            final EpochTaskTrigger trigger,
                            Date currTime) {
        val duration = timeCalculator.executionTime(trigger, currTime)
                .map(Duration::toMillis)
                .orElse(-1L);
        if (-1 == duration) {
            return false;
        }
        val executionTime = new Date(currTime.getTime() + duration);
        val runId = scheduleForExecutionAtTime(topologyId, executionTime, EpochTopologyRunType.SCHEDULED);
        log.debug("Scheduled task {}/{} with delay of {} ms at {}. Reference time: {}",
                  topologyId,
                  runId,
                  duration,
                  executionTime,
                  currTime);
        return true;
    }

    public String scheduleNow(String topologyId) {
        return scheduleForExecutionAtTime(topologyId, new Date(), EpochTopologyRunType.INSTANT);
    }

    public boolean recover(String topologyId, String runId, Date currTime, long duration, EpochTopologyRunType runType) {
        tasks.put(new ExecuteCommand(runId, new Date(currTime.getTime() + duration), topologyId, runType));
        log.trace("Scheduled task {}/{} with delay of {} at {}",
                  topologyId,
                  runId,
                  duration,
                  new Date(currTime.getTime() + duration));
        return true;
    }

    private String scheduleForExecutionAtTime(String topologyId, Date executionTime, EpochTopologyRunType runType) {
        val runId = (runType == EpochTopologyRunType.INSTANT ? "EIR-" : "ESR-") + new SimpleDateFormat(DATE_FORMAT).format(executionTime);
        tasks.put(new ExecuteCommand(runId, executionTime, topologyId, runType));
        return runId;
    }

    private void check(Date currentTime) {
        if(leadershipManager.isLeader()) {
            processQueuedTask(currentTime);
        }
        else {
            log.info("Skipped execution as node is not the leader");
        }
    }

    private void processQueuedTask(Date currentTime) {
        while (true) {
            val executeCommand = tasks.peek();
            var canContinue = false;
            if (executeCommand == null) {
                log.trace("Nothing queued... will sleep again");
            }
            else {
                log.trace("pulled exec command for: {}", executeCommand.getTopologyId());

                if (currentTime.before(executeCommand.getNextExecutionTime())) {
                    log.trace("Found non-executable earliest task: {}/{}",
                              executeCommand.getTopologyId(),
                              executeCommand.getRunId());
                }
                else {
                    canContinue = true;
                }
            }
            if (!canContinue) {
                log.trace("Nothing to do now, will try again later");
                break;
            }
            try {
                executorService.submit(() -> taskCompleted.dispatch(
                        new TaskData(executeCommand.getTopologyId(),
                                     topologyExecutor.execute(executeCommand).orElse(null),
                                     executeCommand.getRunType())));
                val status = tasks.remove(executeCommand);
                log.trace("{} run {}/{} submitted for execution with status {}",
                          executeCommand.getRunType(), executeCommand.getTopologyId(), executeCommand.getRunId(), status);
            }
            catch (Exception e) {
                log.error("Error scheduling topology task: ", e);
            }
        }
    }

    private void handleTaskCompletion(final TaskData taskData) {
        val tId = taskData.topologyId();
        val rId = taskData.runInfo().getRunId();

        val result = taskData.runInfo().getState();
        if(taskData.runType() == EpochTopologyRunType.INSTANT) {
            log.info("Run {}/{} finished with state: {}. No more runs needed as this was an instant run.", tId, rId, result);
            return;
        }
        if (result == EpochTopologyRunState.COMPLETED) {
            log.info("No further scheduling needed for {}/{}", tId, rId);
            return;
        }
        val trigger = topologyStore.get(tId)
                .map(e -> e.getTopology().getTrigger())
                .orElse(null);
        if(trigger == null) {
            log.info("Topology {} seems to have been deleted, no scheduling needed.", tId);
            return;
        }
        log.debug("{} state for {}/{}. Will try to reschedule for next slot.", result, tId, rId);
        if (!schedule(tId, trigger, new Date())) {
            log.warn("Further scheduling skipped for: {}", tId);
        }
    }


}
