package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyDetails;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@Singleton
public final class Scheduler implements Managed {


    private final ExecutorService executorService;
    private final TopologyExecutor topologyExecutor;

    private final PriorityBlockingQueue<ExecuteCommand> tasks
            = new PriorityBlockingQueue<>(1024, Comparator.comparing(ExecuteCommand::getNextExecutionTime));
    private final ExecutionTimeCalculator timeCalculator = new ExecutionTimeCalculator();

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ConsumingFireForgetSignal<TaskData> taskCompleted = new ConsumingFireForgetSignal<>();

    private Future<?> monitorFuture = null;

    public record TaskData(EpochTopologyDetails topologyDetails, EpochTopologyRunInfo runInfo) { }

    @Inject
    public Scheduler(ExecutorService executorService, TopologyExecutor topologyExecutor) {
        this.executorService = executorService;
        this.topologyExecutor = topologyExecutor;
    }

    @Override
    public void start() throws Exception {
        taskCompleted.connect(this::handleTaskCompletion);
        monitorFuture = executorService.submit(this::check);
        log.info("Started scheduler");
    }

    @Override
    public void stop() throws Exception {
        stopped.set(true);
        if (null != monitorFuture) {
            monitorFuture.get();
        }
        log.info("Monitor shut down");
    }

    public ConsumingFireForgetSignal<TaskData> taskCompleted() {
        return taskCompleted;
    }

    public boolean schedule(final EpochTopologyDetails topologyDetails) {
        val currTime = new Date();
        val duration = timeCalculator.executionTime(topologyDetails.getTopology().getTrigger(), currTime)
                .map(Duration::toMillis)
                .orElse(-1L);
        if (-1 == duration) {
            return false;
        }
        val runId = UUID.randomUUID().toString();
        tasks.put(new ExecuteCommand(runId,
                                     new Date(currTime.getTime() + duration),
                                     topologyDetails));
        log.trace("Scheduled task {}/{} with delay of {} at {}",
                  topologyDetails.getId(),
                  runId,
                  duration,
                  new Date(currTime.getTime() + duration));
        return true;
    }


    private void check() {
        var done = false;
        while (!done) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                log.warn("Monitor thread interrupted");
                Thread.currentThread().interrupt();
                return;
            }
            if (stopped.get()) {
                log.info("Stop called. Exiting monitor thread");
                return;
            }
            processQueuedTask();
        }
        log.info("Exiting monitor thread");
    }

    private void processQueuedTask() {
        while (true) {
            val currTime = new Date();
            val executeCommand = tasks.peek();
            var canContinue = false;
            if (executeCommand == null) {
                log.trace("Nothing queued... will sleep again");
            }
            else {
                log.trace("pulled exec command for: {}", executeCommand.getTopology().getId());

                if (currTime.before(executeCommand.getNextExecutionTime())) {
                    log.trace("Found non-executable earliest task: {}/{}",
                              executeCommand.getTopology().getId(),
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
                        new TaskData(executeCommand.getTopology(),
                                     topologyExecutor.execute(executeCommand).orElse(null))));
                tasks.remove(executeCommand);
            }
            catch (Exception e) {
                log.error("Error scheduling topology task: ", e);
            }
        }
    }

    private void handleTaskCompletion(final TaskData taskData) {
        val tId = taskData.topologyDetails().getTopology().getName();
        val rId = taskData.runInfo().getRunId();
        val result = taskData.runInfo().getState();

        if (result == EpochTopologyRunState.COMPLETED) {
            log.info("No further scheduling needed for {}/{}", tId, rId);
            return;
        }

        log.debug("{} state for {}/{}. Will try to reschedule for next slot.", result, tId, rId);
        if (!schedule(taskData.topologyDetails())) {
            log.warn("Further scheduling skipped for: {}", taskData.topologyDetails().getTopology().getName());
        }
    }


}
